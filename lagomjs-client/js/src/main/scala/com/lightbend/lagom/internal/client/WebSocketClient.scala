package com.lightbend.lagom.internal.client

import java.net.URI
import java.nio.ByteBuffer

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.lightbend.lagom.internal.api.transport.LagomServiceApiBridge
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.scalajs.dom.CloseEvent
import org.scalajs.dom.Event
import org.scalajs.dom.WebSocket
import org.scalajs.dom.raw.MessageEvent
import play.api.http.Status

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.scalajs.js.typedarray.ArrayBuffer
import scala.scalajs.js.typedarray.TypedArrayBuffer
import scala.scalajs.js.typedarray.TypedArrayBufferOps._

private[lagom] abstract class WebSocketClient()(implicit ec: ExecutionContext) extends LagomServiceApiBridge {

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

    override def subscribe(subscriber: Subscriber[_ >: ByteString]): Unit = {
      if (!hasSubscription) {
        hasSubscription = true

        // Close the socket if the source subscriber cancels
        subscriber.onSubscribe(new Subscription {
          override def request(n: Long): Unit = {}
          override def cancel(): Unit         = socket.close()
        })

        // Forward messages from the socket to the source subscriber
        socket.onmessage = (message: MessageEvent) => {
          // The message data should be either an ArrayBuffer or a String
          // It should not be a Blob because the socket binaryType was set
          val data = message.data match {
            case buffer: ArrayBuffer => ByteString.apply(TypedArrayBuffer.wrap(buffer))
            case data                => ByteString.fromString(data.toString)
          }
          subscriber.onNext(data)
        }
        // Complete the source subscriber with an error if the socket encounters an error
        socket.onerror = (event: Event) => {
          subscriber.onError(new RuntimeException(s"WebSocket error: ${event.`type`}"))
        }
        // Complete the source subscriber when the socket closes based on the close code
        socket.onclose = (event: CloseEvent) => {
          if (event.code == NormalClosure) {
            // Successfully complete the source subscriber because the socket closed normally
            subscriber.onComplete()
          } else {
            // Complete the source subscriber with an error because the socket closed with an error
            // Parse the error reason as an exception
            val bytes = ByteString.fromString(event.reason)
            val exception =
              exceptionSerializerDeserializeWebSocketException(exceptionSerializer, event.code, requestProtocol, bytes)
            subscriber.onError(exception)
          }
        }
      } else {
        subscriber.onError(new IllegalStateException("This publisher only supports one subscriber"))
      }
    }
  }
}
