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
import play.api.http.Status

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

  private val NormalClosure = 1000

  // Defines the internal buffer size for the websocket when using ServiceCalls containing a Source as it's not possible to use backpressure in the JS websocket implementation
  val maxBufferSize =
    Option(config.getInt("lagom.client.websocket.scalajs.maxBufferSize")).filter(_ != 0).getOrElse(1024)

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
    val socketSource = createSocketSource(socket, exceptionSerializer, messageHeaderProtocol(requestHeader))

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

  private def createSocketSource(
      socket: WebSocket,
      exceptionSerializer: ExceptionSerializer,
      requestProtocol: MessageProtocol
  ): Source[ByteString, NotUsed] = {
    // Create a buffer between the back-pressure unaware WebSocket and back-pressure aware stream
    val (buffer, socketSource) = Source.queue[ByteString](maxBufferSize, OverflowStrategy.fail).preMaterialize()

    // Forward messages from the socket to the buffer
    // Start filling the buffer even before the stream is connected to prevent loss of elements
    socket.onmessage = { message =>
      // The message data should be either an ArrayBuffer or a String
      // It should not be a Blob because the socket binaryType was set
      val data = message.data match {
        case buffer: ArrayBuffer => ByteString.apply(TypedArrayBuffer.wrap(buffer))
        case data                => ByteString.fromString(data.toString)
      }

      buffer.offer(data).onComplete {
        case Failure(exception) => throw exception
        case _                  =>
      }
    }

    // Fail the buffer and ultimately the stream with an error if the socket encounters an error
    socket.onerror = event => buffer.fail(new RuntimeException(s"WebSocket error: ${event.`type`}"))

    // Complete the buffer and ultimately the stream when the socket closes based on the close code
    // This should ensure that pending elements are delivered before completion
    socket.onclose = event => {
      if (event.code == NormalClosure) {
        buffer.complete()
      } else {
        // Complete with an error because the socket closed with an error
        // Parse the error reason as an exception
        val bytes = ByteString.fromString(event.reason)
        val exception =
          exceptionSerializerDeserializeWebSocketException(exceptionSerializer, event.code, requestProtocol, bytes)
        buffer.fail(exception)
      }
    }

    // Close the socket when the buffer completes
    buffer.watchCompletion().onComplete(_ => socket.close())

    socketSource
  }
}
