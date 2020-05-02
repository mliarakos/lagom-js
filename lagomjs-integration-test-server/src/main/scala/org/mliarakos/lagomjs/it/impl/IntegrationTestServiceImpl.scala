package org.mliarakos.lagomjs.it.impl

import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.lightbend.lagom.scaladsl.server.ServerServiceCall
import org.mliarakos.lagomjs.it.api.IntegrationTestService

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Random

class IntegrationTestServiceImpl extends IntegrationTestService {

  override def greeting = ServerServiceCall { _ =>
    Future.successful(s"Welcome!")
  }

  override def hello(name: String) = ServerServiceCall { _ =>
    Future.successful(s"Hello $name!")
  }

  override def random(count: Int) = ServerServiceCall { _ =>
    if (count < 1) {
//      Future.failed(NonPositiveIntegerException(count))
      Future.failed(new RuntimeException(count.toString))
    } else {
      val numbers = Seq.fill(count)(Random.nextInt(10) + 1)
      Future.successful(numbers)
    }
  }

//  override def ping = ServerServiceCall { request =>
//    val message = s"Hello ${request.name}!"
//    Future.successful(Pong(message))
//  }

  override def tick(interval: Int) = ServerServiceCall { message =>
    if (interval < 1) {
//      Future.failed(NonPositiveIntegerException(interval))
      Future.failed(new RuntimeException(interval.toString))
    } else {
      val source = Source.tick(Duration.Zero, interval.milliseconds, message).mapMaterializedValue(_ => NotUsed)
      Future.successful(source)
    }
  }

  override def echo = ServerServiceCall { source =>
    Future.successful(source)
  }

  override def binary = ServerServiceCall { _ =>
    val bytes = Array.ofDim[Byte](16)
    def nextByteString: ByteString = {
      Random.nextBytes(bytes)
      ByteString.apply(bytes)
    }

    val source = Source
      .tick(Duration.Zero, 1.second, NotUsed)
      .map(_ => nextByteString)
      .mapMaterializedValue(_ => NotUsed)
    Future.successful(source)
  }

}
