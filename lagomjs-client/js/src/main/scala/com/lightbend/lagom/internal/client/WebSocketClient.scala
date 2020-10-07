package com.lightbend.lagom.internal.client

import java.net.URI
import java.nio.ByteBuffer

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.OverflowStrategy
import akka.stream.StreamDetachedException
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.lightbend.lagom.internal.api.transport.LagomServiceApiBridge
import com.typesafe.config.Config
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.scalajs.dom.CloseEvent
import org.scalajs.dom.Event
import org.scalajs.dom.WebSocket
import org.scalajs.dom.raw.MessageEvent
import play.api.http.Status

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.scalajs.js.typedarray.ArrayBuffer
import scala.scalajs.js.typedarray.TypedArrayBuffer
import scala.scalajs.js.typedarray.TypedArrayBufferOps._
import scala.util.Failure
import scala.util.Success

private[lagom] abstract class WebSocketClient(config: Config)(implicit ec: ExecutionContext, materializer: Materializer)
    extends LagomServiceApiBridge {

  // Defines the internal buffer size for the websocket when using ServiceCalls containing a Source as it's not possible to use backpressure in the JS websocket implementation
  val maxBufferSize = Option(config.getInt("lagom.client.websocket.scalajs.maxBufferSize")).filter(_!=0).getOrElse(1024)

  /**
   * Connect to the given URI
   */
  def connect(
      exceptionSerializer: ExceptionSerializer,
      requestHeader: RequestHeader,
      outgoing: Source[ByteString, NotUsed]
  ): Future[(ResponseHeader, Source[ByteString, NotUsed])] = {
    // Convert http URI to ws URI
    val uri = requestHeaderUri(requestHeader).normalize
    val scheme = uri.getScheme.toLowerCase match {
      case "http"  => "ws"
      case "https" => "wss"
      case _       => throw new RuntimeException(s"Unsupported URI scheme ${uri.getScheme}")
    }
    val url = new URI(scheme, uri.getAuthority, uri.getPath, uri.getQuery, uri.getFragment).toString

    // Open the socket and set its binaryType so that binary data can be parsed
    val socket = new WebSocket(url)
    socket.binaryType = "arraybuffer"

    // Create sink that will receive the outgoing data from the request source and send it to the socket
    val subscriber = new WebSocketSubscriber(socket, exceptionSerializer)
    val socketSink = Sink.fromSubscriber(subscriber)
    // Create source that will receive the incoming socket data and send it to the response source
    val publisher    = new WebSocketPublisher(socket, exceptionSerializer, messageHeaderProtocol(requestHeader))
    val socketSource = Source.fromPublisher(publisher)
    // Create flow that represents sending data to and receiving data from the socket
    // The sending side is: socketSink -> socket -> service
    // The receiving side is: service -> socket -> socketSource
    // The sink and source are not connected in Akka, the socket is the intermediary that connects them
    val clientConnection = Flow.fromSinkAndSource(socketSink, socketSource)

    val promise = Promise[(ResponseHeader, Source[ByteString, NotUsed])]()

    // Create artificial response header because it is not possible to get it from a WebSocket in JavaScript
    // Use the HTTP 101 response code to indicate switching protocols to WebSocket
    val protocol       = messageProtocolFromContentTypeHeader(None)
    val responseHeader = newResponseHeader(Status.SWITCHING_PROTOCOLS, protocol, Map.empty)

    // Fail if the socket fails to open
    socket.onerror = (event: Event) => {
      promise.failure(new RuntimeException(s"WebSocket error: ${event.`type`}"))
    }
    // Succeed and start the data flow if the socket opens successfully
    socket.onopen = (_: Event) => {
      promise.success((responseHeader, outgoing.via(clientConnection)))
    }

    promise.future
  }

  private class WebSocketSubscriber(
      socket: WebSocket,
      exceptionSerializer: ExceptionSerializer
  ) extends Subscriber[ByteString] {
    override def onSubscribe(s: Subscription): Unit = {
      socket.addEventListener[CloseEvent]("close", (_: CloseEvent) => s.cancel())
      s.request(Long.MaxValue)
    }

    override def onNext(t: ByteString): Unit = {
      val data = t.asByteBuffer
      val buffer =
        if (data.hasTypedArray()) {
          data.typedArray().subarray(data.position, data.limit).buffer
        } else {
          ByteBuffer.allocateDirect(data.remaining).put(data).typedArray().buffer
        }
      socket.send(buffer)
    }

    override def onError(t: Throwable): Unit = {
      val rawExceptionMessage = exceptionSerializerSerialize(exceptionSerializer, t, Nil)
      val code                = rawExceptionMessageWebSocketCode(rawExceptionMessage)
      val message             = rawExceptionMessageMessageAsText(rawExceptionMessage)
      socket.close(code, message)
    }

    override def onComplete(): Unit = {
      socket.close()
    }
  }

  private class WebSocketPublisher(
      socket: WebSocket,
      exceptionSerializer: ExceptionSerializer,
      requestProtocol: MessageProtocol
  ) extends Publisher[ByteString] {
    private val NormalClosure   = 1000
    private var hasSubscription = false

    // acts as a buffer between the backpressare unaware Websocket and backpressure aware reactive-streams Subscriber
    val (messageSource, messageSink) = Source
      .queue[ByteString](maxBufferSize, OverflowStrategy.fail)
      .toMat(Sink.queue())(Keep.both)
      .run()

    // fill buffer even before a subscriber is connected, otherwise we might loose elements
    socket.onmessage = { message =>
      // The message data should be either an ArrayBuffer or a String
      // It should not be a Blob because the socket binaryType was set
      val data = message.data match {
          case buffer: ArrayBuffer => ByteString.apply(TypedArrayBuffer.wrap(buffer))
          case data                => ByteString.fromString(data.toString)
        }

      messageSource.offer(data).onComplete {
        case Failure(exception) =>
          throw exception
        case _ =>
      }
    }

    socket.onerror = event =>
      messageSource.fail(new RuntimeException(s"WebSocket error: ${event.`type`}"))

    // acting on the buffer instead of directly on the subscriber e.g. to make sure pending elements are delivered before completion
    socket.onclose = event => {
      if (event.code == NormalClosure) {
        messageSource.complete()
      } else {
        // Complete the subscriber with an error because the socket closed with an error
        // Parse the error reason as an exception
        val bytes = ByteString.fromString(event.reason)
        val exception =
          exceptionSerializerDeserializeWebSocketException(exceptionSerializer, event.code, requestProtocol, bytes)
        messageSource.fail(exception)
      }
    }

    var completed = false

    override def subscribe(subscriber: Subscriber[_ >: ByteString]): Unit = {
      if (!hasSubscription) {
        hasSubscription = true

        // Configure the subscriber to start the stream
        subscriber.onSubscribe(new Subscription {
          override def request(n: Long): Unit = {
            // pull as many elements as requested, but only as long as element Source has not completed
            def pull(): Unit = {
              messageSink.pull().onComplete {
                case Failure(_: StreamDetachedException) =>
                // happens when subscriber requests more items than available or Subscription.request() is called before all requested elements have been onNext'ed

                case Failure(exception) =>
                  subscriber.onError(exception)

                case Success(Some(data)) =>
                  subscriber.onNext(data)
                  pull()

                case Success(None) =>
                  subscriber.onComplete()
                  completed = true
              }
            }
            if (!completed) pull()
          }
          // Close the socket if the subscriber cancels
          override def cancel(): Unit = socket.close()
        })
      } else {
        subscriber.onError(new IllegalStateException("This publisher only supports one subscriber"))
      }
    }
  }
}
