package com.lightbend.lagom.internal.client

import java.net.URI
import java.nio.ByteBuffer

import akka.NotUsed
import akka.stream.scaladsl.{ Flow, Sink, Source }
import akka.util.ByteString
import com.lightbend.lagom.internal.api.transport.LagomServiceApiBridge
import org.reactivestreams.{ Publisher, Subscriber, Subscription }
import org.scalajs.dom.{ CloseEvent, Event, WebSocket }
import org.scalajs.dom.raw.MessageEvent

import scala.concurrent.{ ExecutionContext, Future, Promise }
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

    val socket  = new WebSocket(url)
    val promise = Promise[(ResponseHeader, Source[ByteString, NotUsed])]()

    val socketSink = Sink.foreach[ByteString](string => {
      val data = string.asByteBuffer
      val buffer =
        if (data.hasTypedArray()) {
          data.typedArray().subarray(data.position, data.limit).buffer
        } else {
          val tempBuffer   = ByteBuffer.allocateDirect(data.remaining)
          val origPosition = data.position
          tempBuffer.put(data)
          data.position(origPosition)
          tempBuffer.typedArray().buffer
        }
      socket.send(buffer)
    })
    val socketSource     = Source.fromPublisher(new WebSocketPublisher(socket))
    val clientConnection = Flow.fromSinkAndSource(socketSink, socketSource)

    // TODO: what do to about missing headers
    val protocol       = messageProtocolFromContentTypeHeader(None)
    val responseHeader = newResponseHeader(200, protocol, Map.empty)

    socket.onerror = (_: Event) => {
      promise.failure(new Exception("WebSocket error"))
    }
    socket.onopen = (_: Event) => {
      promise.success((responseHeader, outgoing.via(clientConnection)))
    }

    promise.future
  }

}

private class WebSocketPublisher(socket: WebSocket) extends Publisher[ByteString] {

  private var hasSubscription: Boolean = false

  override def subscribe(subscriber: Subscriber[_ >: ByteString]): Unit = {
    if (!hasSubscription) {
      hasSubscription = true

      subscriber.onSubscribe(new Subscription {
        override def request(n: Long): Unit = {}

        override def cancel(): Unit = {
          socket.close()
        }
      })

      socket.onmessage = (message: MessageEvent) => {
        subscriber.onNext(ByteString.fromString(message.data.toString))
      }
      socket.onerror = (_: Event) => {
        subscriber.onError(new Exception("WebSocket error"))
      }
      // TODO: handle CloseEvent states
      socket.onclose = (_: CloseEvent) => {
        subscriber.onComplete()
      }
    } else subscriber.onError(new IllegalStateException("This publisher only supports one subscriber"))
  }

}
