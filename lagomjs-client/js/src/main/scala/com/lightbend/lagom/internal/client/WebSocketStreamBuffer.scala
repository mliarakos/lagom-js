package com.lightbend.lagom.internal.client

import akka.stream.BufferOverflowException
import akka.util.ByteString
import com.lightbend.lagom.internal.client.WebSocketStreamBuffer._
import org.reactivestreams.Subscriber
import org.scalajs.dom.WebSocket

import scala.collection.mutable
import scala.scalajs.js.typedarray.ArrayBuffer
import scala.scalajs.js.typedarray.TypedArrayBuffer

private[lagom] class WebSocketStreamBuffer(
    socket: WebSocket,
    bufferSize: Int,
    deserializeException: (Int, ByteString) => Throwable
) {
  private var state: State = Queueing(bufferSize)(this, socket)

  // Forward messages from the socket to the buffer
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
      this.close()
    } else {
      // Fail because the socket closed with an error
      // Parse the error reason as an exception
      val bytes     = ByteString.fromString(event.reason)
      val exception = deserializeException(event.code, bytes)
      this.error(exception)
    }
  }

  def attach(subscriber: Subscriber[_ >: ByteString]): Unit = state.attach(subscriber)
  def addDemand(n: Long): Unit                              = state.addDemand(n)

  private def enqueue(message: ByteString): Unit = state.enqueue(message)
  private def close(): Unit                      = state.close()
  private def error(exception: Throwable): Unit  = state.error(exception)

  private def transition(next: State): Unit = state = next
}

private object WebSocketStreamBuffer {
  val NormalClosure          = 1000
  val ApplicationFailureCode = 4000

  sealed trait State {
    def enqueue(message: ByteString): Unit
    def attach(subscriber: Subscriber[_ >: ByteString]): Unit
    def addDemand(n: Long): Unit
    def close(): Unit
    def error(exception: Throwable): Unit
  }

  sealed trait QueueSize {
    def bufferSize: Int
    def queue: mutable.Queue[ByteString]

    protected def checkSize: Option[Throwable] = {
      if ((queue.size + 1) > bufferSize) Some(BufferOverflowException(s"Exceeded buffer size of $bufferSize"))
      else None
    }
  }

  sealed trait Fulfill {
    def queue: mutable.Queue[ByteString]
    def subscriber: Subscriber[_ >: ByteString]

    protected def fulfill(demand: Long): Long = {
      val num = Math.min(queue.size, demand)
      for (_ <- 1L to num) subscriber.onNext(queue.dequeue)

      demand - num
    }
  }

  case class Queueing(bufferSize: Int)(implicit buffer: WebSocketStreamBuffer, socket: WebSocket)
      extends State
      with QueueSize {
    val queue: mutable.Queue[ByteString] = mutable.Queue.empty

    override def enqueue(message: ByteString): Unit = {
      checkSize.fold[Unit]({
        queue.enqueue(message)
      })(exception => {
        val failed = Failed(exception)
        buffer.transition(failed)
      })
    }

    override def attach(subscriber: Subscriber[_ >: ByteString]): Unit = {
      val streaming = Streaming(queue, bufferSize, subscriber)
      buffer.transition(streaming)
    }

    override def addDemand(n: Long): Unit = {
      val exception = new UnsupportedOperationException("No subscriber is attached")
      val failed    = Failed(exception)
      buffer.transition(failed)
    }

    override def close(): Unit = {
      val full = Full(queue)
      buffer.transition(full)
    }

    override def error(exception: Throwable): Unit = {
      val failed = Failed(exception)
      buffer.transition(failed)
    }
  }

  case class Full(queue: mutable.Queue[ByteString])(implicit buffer: WebSocketStreamBuffer, socket: WebSocket)
      extends State {

    override def enqueue(message: ByteString): Unit = {
      val exception = new UnsupportedOperationException("Can not add messages after the buffer is closed")
      val failed    = Failed(exception)
      buffer.transition(failed)
    }

    override def attach(subscriber: Subscriber[_ >: ByteString]): Unit = {
      val draining = Draining(queue, subscriber)
      buffer.transition(draining)
    }

    override def addDemand(n: Long): Unit = {
      val exception = new UnsupportedOperationException("No subscriber is attached")
      val failed    = Failed(exception)
      buffer.transition(failed)
    }

    override def close(): Unit = {}

    override def error(exception: Throwable): Unit = {
      val failed = Failed(exception)
      buffer.transition(failed)
    }
  }

  case class Streaming(
      queue: mutable.Queue[ByteString],
      bufferSize: Int,
      subscriber: Subscriber[_ >: ByteString]
  )(implicit buffer: WebSocketStreamBuffer, socket: WebSocket)
      extends State
      with QueueSize
      with Fulfill {
    private var demand = 0L

    override def enqueue(message: ByteString): Unit = {
      checkSize.fold[Unit]({
        queue.enqueue(message)
        demand = fulfill(demand)
      })(exception => {
        val failed = Failed(exception, Some(subscriber))
        buffer.transition(failed)
      })
    }

    override def attach(subscriber: Subscriber[_ >: ByteString]): Unit = {
      val exception = new UnsupportedOperationException("Buffer already has attached subscriber")
      val failed    = Failed(exception)
      buffer.transition(failed)
    }

    override def addDemand(n: Long): Unit = {
      demand = fulfill(demand + n)
    }

    override def close(): Unit = {
      val state: State = {
        if (queue.nonEmpty) Draining(queue, subscriber, demand)
        else Completed(subscriber)
      }
      buffer.transition(state)
    }

    override def error(exception: Throwable): Unit = {
      val failed = Failed(exception, Some(subscriber))
      buffer.transition(failed)
    }
  }

  case class Draining(
      queue: mutable.Queue[ByteString],
      subscriber: Subscriber[_ >: ByteString],
      initialDemand: Long = 0L
  )(implicit buffer: WebSocketStreamBuffer, socket: WebSocket)
      extends State
      with Fulfill {
    private var demand = initialDemand

    override def enqueue(message: ByteString): Unit = {
      val exception = new UnsupportedOperationException("Can not add messages after the buffer is closed")
      val failed    = Failed(exception)
      buffer.transition(failed)
    }

    override def attach(subscriber: Subscriber[_ >: ByteString]): Unit = {
      val exception = new UnsupportedOperationException("Buffer already has attached subscriber")
      val failed    = Failed(exception)
      buffer.transition(failed)
    }

    override def addDemand(n: Long): Unit = {
      demand = fulfill(demand + n)
      if (queue.isEmpty) {
        val completed = Completed(subscriber)
        buffer.transition(completed)
      }
    }

    override def close(): Unit = {}

    override def error(exception: Throwable): Unit = {
      val failed = Failed(exception, Some(subscriber))
      buffer.transition(failed)
    }
  }

  case class Completed(subscriber: Subscriber[_])(implicit buffer: WebSocketStreamBuffer, socket: WebSocket)
      extends State {
    socket.close()
    subscriber.onComplete()

    override def enqueue(message: ByteString): Unit                    = fail()
    override def attach(subscriber: Subscriber[_ >: ByteString]): Unit = fail()
    override def addDemand(n: Long): Unit                              = {}
    override def close(): Unit                                         = {}
    override def error(exception: Throwable): Unit                     = fail()

    private def fail(): Unit = {
      throw new UnsupportedOperationException("Buffer is complete")
    }
  }

  case class Failed(
      exception: Throwable,
      subscriber: Option[Subscriber[_]] = None
  )(implicit buffer: WebSocketStreamBuffer, socket: WebSocket)
      extends State {
    socket.close(ApplicationFailureCode)
    subscriber.foreach(_.onError(exception))

    override def enqueue(message: ByteString): Unit                    = {}
    override def attach(subscriber: Subscriber[_ >: ByteString]): Unit = subscriber.onError(exception)
    override def addDemand(n: Long): Unit                              = {}
    override def close(): Unit                                         = {}
    override def error(exception: Throwable): Unit                     = {}
  }

}
