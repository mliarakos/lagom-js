package org.mliarakos.lagomjs.it.api

import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.lightbend.lagom.scaladsl.api._
import com.lightbend.lagom.scaladsl.api.transport.Method

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

  def testStreamingRequest(num: Int): ServiceCall[Source[String, NotUsed], Seq[String]]

  def testBoundedStreamingResponse(num: Int): ServiceCall[String, Source[String, NotUsed]]

  def testUnboundedStreamingResponse: ServiceCall[String, Source[String, NotUsed]]

  def testBoundedStreaming(num: Int): ServiceCall[Source[String, NotUsed], Source[String, NotUsed]]

  def testUnboundedStreaming: ServiceCall[Source[String, NotUsed], Source[String, NotUsed]]

  def testStreamingBinary(num: Int): ServiceCall[Byte, Source[ByteString, NotUsed]]

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
        pathCall("/stream/request?num", testStreamingRequest _),
        pathCall("/stream/response/bounded?num", testBoundedStreamingResponse _),
        pathCall("/stream/response/unbounded", testUnboundedStreamingResponse _),
        pathCall("/stream/both/bounded?num", testBoundedStreaming _),
        pathCall("/stream/both/unbounded", testUnboundedStreaming _),
        pathCall("/stream/binary?num", testStreamingBinary _)
      )
      .withAcls(
        ServiceAcl.forMethodAndPathRegex(Method.OPTIONS, "/.*")
      )
  }

}
