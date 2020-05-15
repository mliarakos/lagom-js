package org.mliarakos.lagomjs.it.api

import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.lightbend.lagom.scaladsl.api._
import com.lightbend.lagom.scaladsl.api.transport.Method

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

  def testStreamingResponse: ServiceCall[String, Source[String, NotUsed]]

//  def echo: ServiceCall[Source[String, NotUsed], Source[String, NotUsed]]

//  def binary: ServiceCall[NotUsed, Source[ByteString, NotUsed]]

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
        pathCall("/stream/response", testStreamingResponse _),
//        restCall(Method.GET, "/hello/:name", hello _),
//        restCall(Method.GET, "/random?count", random _),
//        restCall(Method.POST, "/ping", ping),
//        pathCall("/tick/:interval", tick _),
//        pathCall("/echo", echo),
//        pathCall("/binary", binary)
      )
      .withAcls(
        ServiceAcl.forMethodAndPathRegex(Method.OPTIONS, "/.*")
      )
  }

}
