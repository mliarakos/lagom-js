/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.client

import java.net.URI
import java.net.URLEncoder
import java.nio.ByteBuffer

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.lightbend.lagom.internal.api.HeaderUtils
import com.lightbend.lagom.internal.api.transport.LagomServiceApiBridge
import org.scalajs.dom.XMLHttpRequest
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.ext.AjaxException
import play.api.http.HeaderNames
import play.api.libs.streams.AkkaStreams

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

private[lagom] abstract class ClientServiceCallInvoker[Request, Response](
    serviceName: String,
    path: String,
    queryParams: Map[String, Seq[String]]
)(implicit ec: ExecutionContext, mat: Materializer)
    extends LagomServiceApiBridge {

  import ClientServiceCallInvoker._

  val descriptor: Descriptor
  val serviceLocator: ServiceLocator
  val call: Call[Request, Response]
  def headerFilter: HeaderFilter = descriptorHeaderFilter(descriptor)

  def doInvoke(
      request: Request,
      requestHeaderHandler: RequestHeader => RequestHeader
  ): Future[(ResponseHeader, Response)] = {
    serviceLocatorDoWithService(
      serviceLocator,
      descriptor,
      call,
      uri => {
        val queryString = if (queryParams.nonEmpty) {
          queryParams
            .flatMap {
              case (name, values) =>
                values.map(value => URLEncoder.encode(name, "utf-8") + "=" + URLEncoder.encode(value, "utf-8"))
            }
            .mkString("?", "&", "")
        } else ""
        val url = s"$uri$path$queryString"

        val method = methodForCall(call)

        val requestSerializer  = callRequestSerializer(call)
        val serializer         = messageSerializerSerializerForRequest[Request, Nothing](requestSerializer)
        val responseSerializer = callResponseSerializer(call)

        val requestHeader = requestHeaderHandler(
          newRequestHeader(
            method,
            URI.create(url),
            negotiatedSerializerProtocol(serializer),
            messageSerializerAcceptResponseProtocols(responseSerializer),
            Option(newServicePrincipal(serviceName)),
            Map.empty
          )
        )

        val requestSerializerStreamed  = messageSerializerIsStreamed(requestSerializer)
        val responseSerializerStreamed = messageSerializerIsStreamed(responseSerializer)

        val result: Future[(ResponseHeader, Response)] =
          (requestSerializerStreamed, responseSerializerStreamed) match {
            case (false, false) =>
              makeStrictCall(
                headerFilterTransformClientRequest(headerFilter, requestHeader),
                requestSerializer.asInstanceOf[MessageSerializer[Request, ByteString]],
                responseSerializer.asInstanceOf[MessageSerializer[Response, ByteString]],
                request
              )

            case (false, true) =>
              makeStreamedResponseCall(
                headerFilterTransformClientRequest(headerFilter, requestHeader),
                requestSerializer.asInstanceOf[MessageSerializer[Request, ByteString]],
                responseSerializer.asInstanceOf[MessageSerializer[Response, AkkaStreamsSource[ByteString, NotUsed]]],
                request
              )

            case (true, false) =>
              makeStreamedRequestCall(
                headerFilterTransformClientRequest(headerFilter, requestHeader),
                requestSerializer.asInstanceOf[MessageSerializer[Request, AkkaStreamsSource[ByteString, NotUsed]]],
                responseSerializer.asInstanceOf[MessageSerializer[Response, ByteString]],
                request
              )

            case (true, true) =>
              makeStreamedCall(
                headerFilterTransformClientRequest(headerFilter, requestHeader),
                requestSerializer.asInstanceOf[MessageSerializer[Request, AkkaStreamsSource[ByteString, NotUsed]]],
                responseSerializer.asInstanceOf[MessageSerializer[Response, AkkaStreamsSource[ByteString, NotUsed]]],
                request
              )
          }

        result
      }
    ).map {
      case Some(response) => response
      case None =>
        throw new IllegalStateException(s"Service ${descriptorName(descriptor)} was not found by service locator")
    }
  }

  /**
   * A call that has a strict request and a streamed response.
   *
   * Currently implemented using a WebSocket, and sending the request as the first and only message.
   */
  private def makeStreamedResponseCall(
      requestHeader: RequestHeader,
      requestSerializer: MessageSerializer[Request, ByteString],
      responseSerializer: MessageSerializer[_, AkkaStreamsSource[ByteString, NotUsed]],
      request: Request
  ): Future[(ResponseHeader, Response)] = {
    val serializer = messageSerializerSerializerForRequest[Request, ByteString](requestSerializer)

    // We have a single source, followed by a maybe source (that is, a source that never produces any message, and
    // never terminates). The maybe source is necessary because we want the response stream to stay open.
    val requestAsStream =
      if (messageSerializerIsUsed(requestSerializer)) {
        Source.single(negotiatedSerializerSerialize(serializer, request)).concat(Source.maybe)
      } else {
        // If it's not used, don't send any message
        Source.maybe[ByteString].mapMaterializedValue(_ => NotUsed)
      }

    doMakeStreamedCall(requestAsStream, serializer, requestHeader).map(
      (deserializeResponseStream(responseSerializer, requestHeader) _).tupled
    )
  }

  /**
   * A call that has a streamed request and a strict response.
   *
   * Currently implemented using a WebSocket, that converts the first message received to the strict message. If no
   * message is received, it assumes the response is an empty message.
   */
  private def makeStreamedRequestCall(
      requestHeader: RequestHeader,
      requestSerializer: MessageSerializer[_, AkkaStreamsSource[ByteString, NotUsed]],
      responseSerializer: MessageSerializer[Response, ByteString],
      request: Request
  ): Future[(ResponseHeader, Response)] = {
    val negotiatedSerializer = messageSerializerSerializerForRequest(
      requestSerializer
        .asInstanceOf[MessageSerializer[AkkaStreamsSource[Any, NotUsed], AkkaStreamsSource[ByteString, NotUsed]]]
    )
    val requestStream =
      negotiatedSerializerSerialize(negotiatedSerializer, request.asInstanceOf[AkkaStreamsSource[Any, NotUsed]])

    for {
      (transportResponseHeader, responseStream) <- doMakeStreamedCall(
        akkaStreamsSourceAsScala(requestStream),
        negotiatedSerializer,
        requestHeader
      )
      // We want to take the first element (if it exists), and then ignore all subsequent elements. Ignoring, rather
      // than cancelling the stream, is important, because this is a WebSocket connection, we want the upstream to
      // still remain open, but if we cancel the stream, the upstream will disconnect too.
      maybeResponse <- responseStream.via(AkkaStreams.ignoreAfterCancellation).runWith(Sink.headOption)
    } yield {
      val bytes          = maybeResponse.getOrElse(ByteString.empty)
      val responseHeader = headerFilterTransformClientResponse(headerFilter, transportResponseHeader, requestHeader)
      val deserializer   = messageSerializerDeserializer(responseSerializer, messageHeaderProtocol(responseHeader))
      responseHeader -> negotiatedDeserializerDeserialize(deserializer, bytes)
    }
  }

  /**
   * A call that is streamed in both directions.
   */
  private def makeStreamedCall(
      requestHeader: RequestHeader,
      requestSerializer: MessageSerializer[_, AkkaStreamsSource[ByteString, NotUsed]],
      responseSerializer: MessageSerializer[_, AkkaStreamsSource[ByteString, NotUsed]],
      request: Request
  ): Future[(ResponseHeader, Response)] = {
    val negotiatedSerializer = messageSerializerSerializerForRequest(
      requestSerializer
        .asInstanceOf[MessageSerializer[AkkaStreamsSource[Any, NotUsed], AkkaStreamsSource[ByteString, NotUsed]]]
    )
    val requestStream =
      negotiatedSerializerSerialize(negotiatedSerializer, request.asInstanceOf[AkkaStreamsSource[Any, NotUsed]])

    doMakeStreamedCall(akkaStreamsSourceAsScala(requestStream), negotiatedSerializer, requestHeader).map(
      (deserializeResponseStream(responseSerializer, requestHeader) _).tupled
    )
  }

  private def deserializeResponseStream(
      responseSerializer: MessageSerializer[_, AkkaStreamsSource[ByteString, NotUsed]],
      requestHeader: RequestHeader
  )(transportResponseHeader: ResponseHeader, response: Source[ByteString, NotUsed]): (ResponseHeader, Response) = {
    val responseHeader = headerFilterTransformClientResponse(headerFilter, transportResponseHeader, requestHeader)

    val deserializer = messageSerializerDeserializer(
      responseSerializer
        .asInstanceOf[MessageSerializer[AkkaStreamsSource[Any, NotUsed], AkkaStreamsSource[ByteString, NotUsed]]],
      messageHeaderProtocol(responseHeader)
    )
    responseHeader -> negotiatedDeserializerDeserialize(deserializer, toAkkaStreamsSource(response))
      .asInstanceOf[Response]
  }

  protected def doMakeStreamedCall(
      requestStream: Source[ByteString, NotUsed],
      requestSerializer: NegotiatedSerializer[_, _],
      requestHeader: RequestHeader
  ): Future[(ResponseHeader, Source[ByteString, NotUsed])]

  /**
   * A call that is strict in both directions.
   */
  private def makeStrictCall(
      requestHeader: RequestHeader,
      requestSerializer: MessageSerializer[Request, ByteString],
      responseSerializer: MessageSerializer[Response, ByteString],
      request: Request
  ): Future[(ResponseHeader, Response)] = {

    val url    = requestHeaderUri(requestHeader).toString
    val method = requestHeaderMethod(requestHeader)

    val headerFilter           = descriptorHeaderFilter(descriptor)
    val transportRequestHeader = headerFilterTransformClientRequest(headerFilter, requestHeader)

    val contentTypeHeader =
      messageProtocolToContentTypeHeader(messageHeaderProtocol(transportRequestHeader)).toSeq
        .map(HeaderNames.CONTENT_TYPE -> _)
    val requestHeaders = messageHeaderHeaders(transportRequestHeader).toSeq.collect {
      case (_, values) if values.nonEmpty => values.head._1 -> values.map(_._2).mkString(", ")
    }
    val acceptHeader = {
      val accept = requestHeaderAcceptedResponseProtocols(requestHeader)
        .flatMap { accept =>
          messageProtocolToContentTypeHeader(accept)
        }
        .mkString(", ")
      if (accept.nonEmpty) Seq(HeaderNames.ACCEPT -> accept)
      else Nil
    }
    val headers = (contentTypeHeader ++ requestHeaders ++ acceptHeader).toMap

    val body =
      if (messageSerializerIsUsed(requestSerializer)) {
        val serializer = messageSerializerSerializerForRequest(requestSerializer)
        val body       = negotiatedSerializerSerialize(serializer, request)
        Some(body)
      } else None

    // Remove User-Agent header because it's not allowed by XMLHttpRequest
    val filteredHeaders = headers - HeaderNames.USER_AGENT
    val data            = body.map(_.asByteBuffer).getOrElse(ByteBuffer.wrap(Array[Byte]()))

    Ajax
      .apply(
        method = method,
        url = url,
        data = data,
        timeout = 0,
        headers = filteredHeaders,
        withCredentials = false,
        responseType = ""
      )
      .recover({
        case e: AjaxException if !e.isTimeout => e.xhr
      })
      .map(xhr => {
        val status  = xhr.status
        val headers = parseHeaders(xhr)
        val body    = ByteString.fromString(Option(xhr.responseText).getOrElse(""))

        // Create the message header
        val contentTypeHeader = headers.get(HeaderNames.CONTENT_TYPE).map(_.mkString(", "))
        val protocol          = messageProtocolFromContentTypeHeader(contentTypeHeader)
        val responseHeaders = headers.map {
          case (key, values) => HeaderUtils.normalize(key) -> values.map(key -> _).toIndexedSeq
        }
        val transportResponseHeader = newResponseHeader(status, protocol, responseHeaders)
        val responseHeader          = headerFilterTransformClientResponse(headerFilter, transportResponseHeader, requestHeader)

        if (status >= 400 && status <= 599) {
          throw exceptionSerializerDeserializeHttpException(
            descriptorExceptionSerializer(descriptor),
            status,
            protocol,
            body
          )
        } else {
          val negotiatedDeserializer =
            messageSerializerDeserializer(responseSerializer, messageHeaderProtocol(responseHeader))
          responseHeader -> negotiatedDeserializerDeserialize(negotiatedDeserializer, body)
        }
      })
  }

}

private object ClientServiceCallInvoker {
  private val header = "(.*?):(.*)".r

  def parseHeaders(xhr: XMLHttpRequest): Map[String, Seq[String]] = {
    xhr
      .getAllResponseHeaders()
      .split("\r\n")
      .flatMap({
        case header(key, values) => Some(key.trim -> values.split(",").map(_.trim).toSeq)
        case _                   => None
      })
      .toMap
  }
}
