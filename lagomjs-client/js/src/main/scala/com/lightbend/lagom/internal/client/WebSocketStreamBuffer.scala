package com.lightbend.lagom.internal.client

import akka.stream.BufferOverflowException
import akka.util.ByteString
import com.lightbend.lagom.internal.client.WebSocketStreamBuffer._
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.scalajs.dom.WebSocket

import scala.collection.mutable
import scala.scalajs.js.typedarray.ArrayBuffer
import scala.scalajs.js.typedarray.TypedArrayBuffer

/**
 * Buffers elements received from a WebSocket for consumption by a reactive stream
 *
 * The buffer is implemented as a finite state machine for use in a JavaScript runtime. It prioritizes delivering
 * elements downstream immediately in response to incoming messages and downstream requests. This is to mitigate the
 * impact of fast sockets consuming the event-loop and preventing downstream consumption of messages. It enforces a
 * maximum number of queued elements and will fail the socket and stream if the maximum is exceeded.
 *
 * The buffer has seven states: Queueing, Full, Streaming, Draining, Completed, Cancelled, and Failed.
 *
 * The Queueing state is when the socket is open, the buffer will receive and queue messages, and no subscriber
 * has been attached. The Full state is when the socket is closed, the buffer will not queue more messages, and no
 * subscriber has been attached. The Streaming state is when the socket is open, the buffer will receive and queue
 * messages, a subscriber is attached, and messages are sent to the subscriber in response to its demand signals. The
 * Draining state is when the socket is closed, the buffer will not queue more messages, a subscriber is attached, and
 * messages are sent to the subscriber in response to its demand signals. The Completed state is when the socket is
 * closed, a subscriber is attached, all queued messages have been sent to the subscriber, and the subscriber is
 * completed. The Cancelled state is when a subscriber is attached and cancelled, causing the socket to be closed and
 * any queued messages to be discarded. The Failed state is when the buffer has failed for any reason, causing the
 * socket to be closed, the subscriber to be completed with an error, and any queued messages to be discarded.
 */
private[lagom] class WebSocketStreamBuffer(
    socket: WebSocket,
    bufferSize: Int,
    deserializeException: (Int, ByteString) => Throwable
) {
  // Start the buffer in the Queueing state
  private var state: State = Queueing(bufferSize)(this, socket)

  // Configure the socket to interact with the buffer

  // Add messages from the socket to the queue
  socket.onmessage = { message =>
    // The message data should be either an ArrayBuffer or a String
    // It should not be a Blob because the socket binaryType was set
    val data = message.data match {
      case buffer: ArrayBuffer => ByteString.apply(TypedArrayBuffer.wrap(buffer))
      case data                => ByteString.fromString(data.toString)
    }

    this.enqueue(data)
  }

  // Fail the buffer and ultimately the stream with an error if the socket encounters an error
  socket.onerror = { event =>
    this.error(new RuntimeException(s"WebSocket error: ${event.`type`}"))
  }

  // Complete the buffer and ultimately the stream when the socket closes based on the close code
  // The buffer will ensure that pending elements are delivered before downstream completion
  socket.onclose = { event =>
    if (event.code == NormalClosure) {
      this.complete()
    } else {
      // Fail because the socket closed with an error
      // Parse the error reason as an exception
      val bytes     = ByteString.fromString(event.reason)
      val exception = deserializeException(event.code, bytes)
      this.error(exception)
    }
  }

  // Delegate all operations to the current state

  def attach(subscriber: Subscriber[_ >: ByteString]): Unit = state.attach(subscriber)
  def addDemand(n: Long): Unit                              = state.addDemand(n)
  def cancel(): Unit                                        = state.cancel()

  private def enqueue(message: ByteString): Unit = state.enqueue(message)
  private def complete(): Unit                   = state.complete()
  private def error(exception: Throwable): Unit  = state.error(exception)

  // Internal method used by the state to transition to a new sate
  private def transition(next: State): Unit = {
    state = next
  }
}

private object WebSocketStreamBuffer {
  val NormalClosure          = 1000
  val ApplicationFailureCode = 4000

  sealed trait State {

    /**
     * Add the given message to the queue
     *
     * @param message the message to add
     */
    def enqueue(message: ByteString): Unit

    /**
     * Attach a reactive streams subscriber
     *
     * @param subscriber the subscriber
     */
    def attach(subscriber: Subscriber[_ >: ByteString]): Unit

    /**
     * Add given demand to the outstanding demand
     *
     * It is called by the reactive streams Subscriber via its Subscription `request` method
     *
     * @param n the demand
     */
    def addDemand(n: Long): Unit

    /**
     * Complete the buffer
     *
     * This indicates that no new elements will be added to the buffer. It is called when the socket closes
     * successfully.
     */
    def complete(): Unit

    /**
     * Cancel the buffer
     *
     * It is called by the reactive streams Subscriber via its Subscription `cancel` method
     */
    def cancel(): Unit

    /**
     * Error the buffer with the given exception
     *
     * This is called in a variety of error conditions, including when the socket closes with an error.
     *
     * @param exception the exception
     */
    def error(exception: Throwable): Unit

  }

  /**
   * Provides reusable state method to check if adding an element would exceed the buffer size
   */
  sealed trait QueueSize {
    def bufferSize: Int
    def queue: mutable.Queue[ByteString]

    /**
     * Check if adding an element would exceed the buffer size, if so returns `Some` exception, otherwise `None`
     *
     * The exception is not thrown in order to avoid exception overhead and because the exception must be passed to
     * the subscriber onError method.
     *
     * @return optional exception if the buffer size is exceeded
     */
    protected def checkSize: Option[Throwable] = {
      if (queue.size >= bufferSize) Some(BufferOverflowException(s"Exceeded buffer size of $bufferSize"))
      else None
    }
  }

  /**
   * Provides reusable state method to fulfill subscriber demand
   */
  sealed trait Fulfill {
    def queue: mutable.Queue[ByteString]
    def subscriber: Subscriber[_ >: ByteString]

    /**
     * Fulfill subscriber demand if possible
     *
     * Fulfill by removing up to demand number of elements elements from the buffer and sending them to the subscriber.
     * The remaining outstanding demand is returned based on how elements where sent.
     *
     * @param demand current demand
     * @return remaining demand
     */
    protected def fulfill(demand: Long): Long = {
      val num = Math.min(queue.size, demand)
      for (_ <- 1L to num) subscriber.onNext(queue.dequeue)

      demand - num
    }
  }

  /**
   * Provides reusable state method to cancel the buffer
   */
  sealed trait Cancelable {
    def socket: WebSocket
    def buffer: WebSocketStreamBuffer

    def cancel(): Unit = {
      val cancelled = Cancelled()(socket)
      buffer.transition(cancelled)
    }
  }

  /**
   * State when the socket is open, the buffer will receive and queue messages, and no subscriber has been attached
   *
   * This state allows elements to be buffered before the stream is set up and a subscriber is attached. If the socket
   * closes and the buffer completes before a subscriber is attached then the state will transition to Full.
   * Otherwise, if a subscriber is attached first, the state will transition to Streaming.
   */
  case class Queueing(bufferSize: Int)(implicit buffer: WebSocketStreamBuffer, socket: WebSocket)
      extends State
      with QueueSize {
    val queue: mutable.Queue[ByteString] = mutable.Queue.empty

    /**
     * Add message to the queue
     *
     * If adding the message would exceed the buffer size then the message is discarded and the buffer transitions to
     * the Failed state.
     */
    override def enqueue(message: ByteString): Unit = {
      checkSize.fold[Unit]({
        queue.enqueue(message)
      })(exception => {
        val failed = Failed(exception)
        buffer.transition(failed)
      })
    }

    /**
     * Attach the given subscriber and transition to the Streaming state
     */
    override def attach(subscriber: Subscriber[_ >: ByteString]): Unit = {
      val streaming = Streaming(queue, bufferSize, PreparedSubscriber(subscriber))
      buffer.transition(streaming)
    }

    /**
     * Transition to the Failed state because this operation is not supported when no subscriber is attached
     */
    override def addDemand(n: Long): Unit = {
      val exception = new UnsupportedOperationException("No subscriber is attached")
      val failed    = Failed(exception)
      buffer.transition(failed)
    }

    /**
     * Transition to the Full state
     *
     * This indicates that no new elements will be added to the buffer. Since the buffer was completed before a
     * subscriber was attached the buffer contains every message.
     */
    override def complete(): Unit = {
      val full = Full(queue)
      buffer.transition(full)
    }

    /**
     * Transition to the Failed state because this operation is not supported when no subscriber is attached
     */
    override def cancel(): Unit = {
      val exception = new UnsupportedOperationException("No subscriber is attached")
      val failed    = Failed(exception)
      buffer.transition(failed)
    }

    /**
     * Error the buffer by transitioning to the Failed state with the given exception
     */
    override def error(exception: Throwable): Unit = {
      val failed = Failed(exception)
      buffer.transition(failed)
    }
  }

  /**
   * State when the socket is closed, the buffer will not queue more messages, and no subscriber has been attached
   *
   * When a subscriber is attached the state will transition to Draining to start emptying the queue by sending
   * elements to the subscriber in response to its demand signals. The queue contains all messages ever sent by the
   * socket because the socket closed and the buffer was completed before a subscriber was attached. The queue may
   * be empty.
   */
  case class Full(queue: mutable.Queue[ByteString])(implicit buffer: WebSocketStreamBuffer, socket: WebSocket)
      extends State {

    /**
     * Transition to the Failed state because this operation is not supported after the buffer is completed
     */
    override def enqueue(message: ByteString): Unit = {
      val exception = new UnsupportedOperationException("Can not add messages after the buffer is completed")
      val failed    = Failed(exception)
      buffer.transition(failed)
    }

    /**
     * Attach the given subscriber and transition to the Draining state
     */
    override def attach(subscriber: Subscriber[_ >: ByteString]): Unit = {
      val draining = Draining(queue, PreparedSubscriber(subscriber))
      buffer.transition(draining)
    }

    /**
     * Transition to the Failed state because this operation is not supported when no subscriber is attached
     */
    override def addDemand(n: Long): Unit = {
      val exception = new UnsupportedOperationException("No subscriber is attached")
      val failed    = Failed(exception)
      buffer.transition(failed)
    }

    /**
     * Ignore because the buffer is already completed
     */
    override def complete(): Unit = {}

    /**
     * Transition to the Failed state because this operation is not supported when no subscriber is attached
     */
    override def cancel(): Unit = {
      val exception = new UnsupportedOperationException("No subscriber is attached")
      val failed    = Failed(exception)
      buffer.transition(failed)
    }

    /**
     * Error the buffer by transitioning to the Failed state with the given exception
     */
    override def error(exception: Throwable): Unit = {
      val failed = Failed(exception)
      buffer.transition(failed)
    }
  }

  /**
   * State when the socket is open, the buffer will receive and queue messages, a subscriber is attached, and messages
   * are sent to the subscriber in response to its demand signals
   *
   * This state streams messages from the socket to the subscriber via the queue. Messages are sent to the subscriber
   * in response to its demand signals. When the socket closes and the buffer completes, then the state will transition
   * to Draining if there are message left in the queue, or Completed if there are no messages left.
   */
  case class Streaming(
      queue: mutable.Queue[ByteString],
      bufferSize: Int,
      preparedSubscriber: PreparedSubscriber
  )(implicit val buffer: WebSocketStreamBuffer, val socket: WebSocket)
      extends State
      with QueueSize
      with Fulfill
      with Cancelable {
    // Cumulative outstanding demand starting at 0
    private var demand = 0L
    val subscriber     = preparedSubscriber.subscriber

    /**
     * Add message to the queue and fulfill demand is possible
     *
     * If adding the message would exceed the buffer size then the message is discarded and the buffer transitions to
     * the Failed state.
     */
    override def enqueue(message: ByteString): Unit = {
      checkSize.fold[Unit]({
        queue.enqueue(message)
        demand = fulfill(demand)
      })(exception => {
        val failed = Failed(exception, subscriber)
        buffer.transition(failed)
      })
    }

    /**
     * Fail the new subscriber
     *
     * Buffer operations continue for the existing subscriber.
     */
    override def attach(subscriber: Subscriber[_ >: ByteString]): Unit = {
      PreparedSubscriber.failSingle(subscriber)
    }

    /**
     * Add and fulfill demand if possible
     */
    override def addDemand(n: Long): Unit = {
      if (n > 0) {
        demand = fulfill(demand + n)
      } else {
        val exception = new IllegalArgumentException(s"Demand of $n is not positive")
        val failed    = Failed(exception, subscriber)
        buffer.transition(failed)
      }
    }

    /**
     * Transition to the Draining state if there are messages in the queue or the Completed state if not
     */
    override def complete(): Unit = {
      val state: State = {
        if (queue.nonEmpty) Draining(queue, preparedSubscriber, demand)
        else Completed(subscriber)
      }
      buffer.transition(state)
    }

    /**
     * Error the buffer by transitioning to the Failed state with the given exception
     */
    override def error(exception: Throwable): Unit = {
      val failed = Failed(exception, subscriber)
      buffer.transition(failed)
    }
  }

  /**
   * State when the socket is closed, the buffer will not queue more messages, a subscriber is attached, and messages
   * are sent to the subscriber in response to its demand signals
   *
   * This state drains the queue by sending messages to the subscriber in response to its demand signals until the
   * queue is empty and it then transitions to Completed.
   */
  case class Draining(
      queue: mutable.Queue[ByteString],
      preparedSubscriber: PreparedSubscriber,
      initialDemand: Long = 0L
  )(implicit val buffer: WebSocketStreamBuffer, val socket: WebSocket)
      extends State
      with Fulfill
      with Cancelable {
    private var demand = initialDemand
    val subscriber     = preparedSubscriber.subscriber

    /**
     * Transition to the Failed state because this operation is not supported after the buffer is completed
     */
    override def enqueue(message: ByteString): Unit = {
      val exception = new UnsupportedOperationException("Can not add messages after the buffer is closed")
      val failed    = Failed(exception, subscriber)
      buffer.transition(failed)
    }

    /**
     * Fail the new subscriber
     *
     * Buffer operations continue for the existing subscriber.
     */
    override def attach(subscriber: Subscriber[_ >: ByteString]): Unit = {
      PreparedSubscriber.failSingle(subscriber)
    }

    /**
     * Add and fulfill demand if possible, completing the buffer if the queue becomes empty
     */
    override def addDemand(n: Long): Unit = {
      if (n > 0) {
        demand = fulfill(demand + n)
        if (queue.isEmpty) {
          val completed = Completed(subscriber)
          buffer.transition(completed)
        }
      } else {
        val exception = new IllegalArgumentException(s"Demand of $n is not positive")
        val failed    = Failed(exception, subscriber)
        buffer.transition(failed)
      }
    }

    /**
     * Ignore because the buffer is already completed
     */
    override def complete(): Unit = {}

    /**
     * Error the buffer by transitioning to the Failed state with the given exception
     */
    override def error(exception: Throwable): Unit = {
      val failed = Failed(exception, subscriber)
      buffer.transition(failed)
    }
  }

  /**
   * State when the socket is closed, a subscriber is attached, all queued messages have been sent to the subscriber,
   * and the subscriber is completed
   *
   * This is the successful terminal state. Transitioning to this state closes the socket and completes the subscriber.
   */
  case class Completed(subscriber: Subscriber[_])(implicit socket: WebSocket) extends State {
    socket.close()
    subscriber.onComplete()

    override def enqueue(message: ByteString): Unit                    = {}
    override def attach(subscriber: Subscriber[_ >: ByteString]): Unit = PreparedSubscriber.failSingle(subscriber)
    override def addDemand(n: Long): Unit                              = {}
    override def complete(): Unit                                      = {}
    override def cancel(): Unit                                        = {}
    override def error(exception: Throwable): Unit                     = {}
  }

  /**
   * State when a subscriber is attached and cancelled, causing the socket to be closed and any queued messages to be
   * discarded
   *
   * This is the alternate terminal state. Transitioning to this state closes the socket.
   */
  case class Cancelled()(implicit socket: WebSocket) extends State {
    socket.close()

    override def enqueue(message: ByteString): Unit                    = {}
    override def attach(subscriber: Subscriber[_ >: ByteString]): Unit = PreparedSubscriber.failSingle(subscriber)
    override def addDemand(n: Long): Unit                              = {}
    override def complete(): Unit                                      = {}
    override def cancel(): Unit                                        = {}
    override def error(exception: Throwable): Unit                     = {}
  }

  /**
   * State when the buffer has failed for any reason, causing the socket to be closed, the subscriber to be
   * completed with an error, and any queued messages to be discarded
   *
   * This is the failed terminal state. Transitioning to this state closes the socket with a failure code and errors
   * the subscriber if one is attached. A subscriber can be attached after failure in case the buffer fails before the
   * stream is set up, in which case the subscriber is immediately errored with the exception.
   */
  case class Failed(exception: Throwable, subscriber: Option[Subscriber[_]] = None)(implicit socket: WebSocket)
      extends State {
    socket.close(ApplicationFailureCode)
    subscriber.foreach(_.onError(exception))

    override def enqueue(message: ByteString): Unit                    = {}
    override def attach(subscriber: Subscriber[_ >: ByteString]): Unit = PreparedSubscriber.fail(subscriber, exception)
    override def addDemand(n: Long): Unit                              = {}
    override def complete(): Unit                                      = {}
    override def cancel(): Unit                                        = {}
    override def error(exception: Throwable): Unit                     = {}
  }

  object Failed {
    def apply(exception: Throwable, subscriber: Subscriber[_])(implicit socket: WebSocket): Failed =
      Failed(exception, Some(subscriber))
  }

  /** Subscription that interacts with the WebSocketStreamBuffer */
  class BufferSubscription(buffer: WebSocketStreamBuffer) extends Subscription {
    // Add request to the outstanding buffer demand
    override def request(n: Long): Unit = buffer.addDemand(n)

    // Cancel the buffer
    // The buffer cancels immediately and does not delivery any pending elements
    override def cancel(): Unit = buffer.cancel()
  }

  /** Dummy subscription used to set up subscriber and in order to immediately error the subscriber */
  object ErrorSubscription extends Subscription {
    override def request(n: Long): Unit = {}
    override def cancel(): Unit         = {}
  }

  /**
   * A subscriber that has been prepared with the appropriate subscription to interact with a buffer
   *
   * The reactive streams specification requires that a subscription be provided to the subscriber prior to any other
   * signals. This is used to ensure that the subscriber is ready for operations before being used.
   */
  case class PreparedSubscriber(subscriber: Subscriber[_ >: ByteString])(implicit buffer: WebSocketStreamBuffer) {
    subscriber.onSubscribe(new BufferSubscription(buffer))
  }

  object PreparedSubscriber {

    /**
     * Fail the given subscriber because another subscriber is already attached
     *
     * @param subscriber the subscriber to fail
     */
    def failSingle(subscriber: Subscriber[_]): Unit = {
      fail(subscriber, new IllegalStateException("This publisher only supports one subscriber"))
    }

    /**
     * Fail the given subscriber with the given exception
     *
     * The reactive streams specification requires that a subscription be provided to the subscriber prior to any other
     * signals. The subscriber is provided a dummy subscription and then immediately failed.
     *
     * @param subscriber the subscriber to fail
     * @param exception the exception
     */
    def fail(subscriber: Subscriber[_], exception: Throwable): Unit = {
      subscriber.onSubscribe(ErrorSubscription)
      subscriber.onError(exception)
    }
  }

}
