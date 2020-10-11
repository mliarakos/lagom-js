package org.mliarakos.lagomjs.it.api

import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.lightbend.lagom.scaladsl.api._
import com.lightbend.lagom.scaladsl.api.transport.Method
import org.mliarakos.lagomjs.it.api.domain.Input
import org.mliarakos.lagomjs.it.api.domain.Output
import org.mliarakos.lagomjs.it.api.exception.TestExceptionSerializer

import scala.collection.immutable._

trait IntegrationTestService extends Service {

  def testCall: ServiceCall[NotUsed, String]

  def testNamedCall: ServiceCall[NotUsed, String]

  def testPathCall(a: String): ServiceCall[NotUsed, String]

  def testPathCallMultiple(a: String, b: String): ServiceCall[NotUsed, String]

  def testPathCallQuery(a: String): ServiceCall[NotUsed, String]

  def testPathCallQueryMultiple(a: String, b: String): ServiceCall[NotUsed, String]

  def testRestGetCall(a: String): ServiceCall[NotUsed, String]

  def testRestPostCall: ServiceCall[Input, Output]

  def testRestPutCall: ServiceCall[Input, Output]

  def testRestDeleteCall(a: String): ServiceCall[NotUsed, String]

  def testRestHeadCall: ServiceCall[NotUsed, NotUsed]

  def testRestPatchCall(a: String): ServiceCall[Input, Output]

  def testStreamingRequest(limit: Int): ServiceCall[Source[Int, NotUsed], Seq[Int]]

  def testEmptyRequestBoundedStreamingResponse(start: Int, end: Int): ServiceCall[NotUsed, Source[Int, NotUsed]]

  def testRequestBoundedStreamingResponse(start: Int): ServiceCall[Int, Source[Int, NotUsed]]

  def testEmptyRequestUnboundedStreamingResponse(start: Int): ServiceCall[NotUsed, Source[Int, NotUsed]]

  def testRequestUnboundedStreamingResponse: ServiceCall[Int, Source[Int, NotUsed]]

  def testBoundedStreaming(limit: Int): ServiceCall[Source[Int, NotUsed], Source[Int, NotUsed]]

  def testUnboundedStreaming: ServiceCall[Source[Int, NotUsed], Source[Int, NotUsed]]

  def testStreamingBinary(num: Int): ServiceCall[Byte, Source[ByteString, NotUsed]]

  def testException: ServiceCall[String, NotUsed]

  def testStreamingImmediateServiceException: ServiceCall[String, Source[String, NotUsed]]

  def testStreamingImmediateStreamException: ServiceCall[String, Source[String, NotUsed]]

  def testStreamingEventualException(start: Int, end: Int): ServiceCall[String, Source[Int, NotUsed]]

  override def descriptor: Descriptor = {
    import Service._
    named("lagomjs-it")
      .withCalls(
        call(testCall),
        namedCall("namedCall", testNamedCall),
        pathCall("/path/:a", testPathCall _),
        pathCall("/path/:a/:b", testPathCallMultiple _),
        pathCall("/query?a", testPathCallQuery _),
        pathCall("/query/multi?a&b", testPathCallQueryMultiple _),
        restCall(Method.GET, "/rest/get/:a", testRestGetCall _),
        restCall(Method.POST, "/rest/post", testRestPostCall _),
        restCall(Method.PUT, "/rest/put", testRestPutCall _),
        restCall(Method.DELETE, "/rest/delete/:a", testRestDeleteCall _),
        restCall(Method.HEAD, "/rest/head", testRestHeadCall _),
        restCall(Method.PATCH, "/rest/patch/:a", testRestPatchCall _),
        pathCall("/stream/request?num", testStreamingRequest _),
        pathCall("/stream/response/bounded/empty?start&end", testEmptyRequestBoundedStreamingResponse _),
        pathCall("/stream/response/bounded/request?start", testRequestBoundedStreamingResponse _),
        pathCall("/stream/response/unbounded/empty?start", testEmptyRequestUnboundedStreamingResponse _),
        pathCall("/stream/response/unbounded/request", testRequestUnboundedStreamingResponse _),
        pathCall("/stream/both/bounded?num", testBoundedStreaming _),
        pathCall("/stream/both/unbounded", testUnboundedStreaming _),
        pathCall("/stream/binary?num", testStreamingBinary _),
        pathCall("/exception/strict", testException),
        pathCall("/exception/streaming/immediate/service", testStreamingImmediateServiceException),
        pathCall("/exception/streaming/immediate/stream", testStreamingImmediateStreamException),
        pathCall("/exception/streaming/eventual?start&end", testStreamingEventualException _)
      )
      .withAcls(
        ServiceAcl.forMethodAndPathRegex(Method.OPTIONS, "/.*")
      )
      .withExceptionSerializer(TestExceptionSerializer)
  }

}
