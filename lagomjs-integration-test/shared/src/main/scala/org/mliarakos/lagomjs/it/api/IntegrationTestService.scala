package org.mliarakos.lagomjs.it.api

import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.lightbend.lagom.scaladsl.api._
import com.lightbend.lagom.scaladsl.api.transport.Method

trait IntegrationTestService extends Service {

  def greeting: ServiceCall[NotUsed, String]

  def hello(name: String): ServiceCall[NotUsed, String]

  def random(count: Int): ServiceCall[NotUsed, Seq[Int]]

//  def ping: ServiceCall[Ping, Pong]

  def tick(interval: Int): ServiceCall[String, Source[String, NotUsed]]

  def echo: ServiceCall[Source[String, NotUsed], Source[String, NotUsed]]

  def binary: ServiceCall[NotUsed, Source[ByteString, NotUsed]]

  override def descriptor: Descriptor = {
    import Service._
    named("lagomjs-it")
      .withCalls(
        restCall(Method.GET, "/greeting", greeting),
        restCall(Method.GET, "/hello/:name", hello _),
        restCall(Method.GET, "/random?count", random _),
//        restCall(Method.POST, "/ping", ping),
        pathCall("/tick/:interval", tick _),
        pathCall("/echo", echo),
        pathCall("/binary", binary)
      )
      .withAcls(
        ServiceAcl.forMethodAndPathRegex(Method.OPTIONS, "/.*")
      )
  }

}
