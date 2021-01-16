package com.lightbend.lagom.internal.client

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.lightbend.lagom.internal.api.transport.LagomServiceApiBridge
import com.typesafe.config.Config
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.scalajs.dom.CloseEvent
import org.scalajs.dom.WebSocket
import org.scalajs.dom.raw.Event
import play.api.http.Status

import java.net.URI
import java.nio.ByteBuffer
import scala.collection.immutable._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.scalajs.js.typedarray.TypedArrayBufferOps._

private[lagom] abstract class WebSocketClient(config: WebSocketClientConfig)(
    implicit ec: ExecutionContext,
    materializer: Materializer
) extends LagomServiceApiBridge {

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
    // Use an event listener and remove it if the socket opens successfully so that the socketSource can
    // use socket onError for error handling
    val openOnError = (event: Event) => {
      promise.failure(new RuntimeException(s"WebSocket error: ${event.`type`}"))
    }
    socket.addEventListener[Event]("error", openOnError)
    // Succeed and start the data flow if the socket opens successfully
    socket.onopen = (_: Event) => {
      socket.removeEventListener[Event]("error", openOnError)
      promise.success((responseHeader, outgoing.via(clientConnection)))
    }

    promise.future
  }

  private class WebSocketSubscriber(
      socket: WebSocket,
      exceptionSerializer: ExceptionSerializer
  ) extends Subscriber[ByteString] {
    override def onSubscribe(s: Subscription): Unit = {
      // Cancel upstream when the socket closes
      // Use an event listener so that the socketSource can use socket onClose
      socket.addEventListener[CloseEvent]("close", (_: CloseEvent) => s.cancel())
      // Request unbounded demand since WebSockets do not support back-pressure
      s.request(Long.MaxValue)
    }

    override def onNext(t: ByteString): Unit = {
      // Convert the message into a JavaScript ArrayBuffer
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
      // Close the socket in error based on the exception
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
    private var hasSubscription = false
    private val buffer          = new WebSocketStreamBuffer(socket, config.bufferSize, deserializeException)

    private def deserializeException(code: Int, bytes: ByteString): Throwable =
      exceptionSerializerDeserializeWebSocketException(exceptionSerializer, code, requestProtocol, bytes)

    override def subscribe(subscriber: Subscriber[_ >: ByteString]): Unit = {
      if (!hasSubscription) {
        hasSubscription = true

        // Attach the subscriber
        // The buffer will send elements in response to requests
        buffer.attach(subscriber)

        subscriber.onSubscribe(new Subscription {
          override def request(n: Long): Unit = buffer.addDemand(n)
          // Close the socket if the subscriber cancels
          override def cancel(): Unit = socket.close()
        })
      } else {
        subscriber.onError(new IllegalStateException("This publisher only supports one subscriber"))
      }
    }
  }

}

private[lagom] sealed trait WebSocketClientConfig {
  def bufferSize: Int
  def maxFrameLength: Int
}

private[lagom] object WebSocketClientConfig {
  def apply(conf: Config): WebSocketClientConfig =
    new WebSocketClientConfigImpl(conf.getConfig("lagom.client.websocket"))

  class WebSocketClientConfigImpl(conf: Config) extends WebSocketClientConfig {
    val bufferSize = conf.getString("bufferSize") match {
      case "unlimited" => Int.MaxValue
      case _           => conf.getInt("bufferSize")
    }
    val maxFrameLength = math.min(Int.MaxValue.toLong, conf.getBytes("frame.maxLength")).toInt
  }
}
