package org.mliarakos.lagomjs.it.impl

import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.lightbend.lagom.scaladsl.server.ServerServiceCall
import org.mliarakos.lagomjs.it.api.IntegrationTestService
import org.mliarakos.lagomjs.it.api.Output
import org.mliarakos.lagomjs.it.api.TestValues

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Random
import scala.collection.immutable._

class IntegrationTestServiceImpl extends IntegrationTestService {

  private val repeat = 10

  override def testCall = ServerServiceCall { _ =>
    Future.successful(TestValues.DEFAULT)
  }

  override def testNamedCall = ServerServiceCall { _ =>
    Future.successful(TestValues.DEFAULT)
  }

  override def testPathCall(a: String) = ServerServiceCall { _ =>
    Future.successful(a)
  }

  override def testPathCallMultiple(a: String, b: String) = ServerServiceCall { _ =>
    Future.successful(a + b)
  }

  override def testPathCallQuery(a: String) = ServerServiceCall { _ =>
    Future.successful(a)
  }

  override def testPathCallQueryMultiple(a: String, b: String) = ServerServiceCall { _ =>
    Future.successful(a + b)
  }

  override def testRestGetCall(a: String) = ServerServiceCall { _ =>
    Future.successful(a)
  }

  override def testRestPostCall = ServerServiceCall { input =>
    val output = Output(input.a, input.b)
    Future.successful(output)
  }

  override def testRestPutCall = ServerServiceCall { input =>
    val output = Output(input.a, input.b)
    Future.successful(output)
  }

  override def testRestDeleteCall(a: String) = ServerServiceCall { _ =>
    Future.successful(a)
  }

  override def testStreamingResponse = ServerServiceCall { message =>
    val source = Source(List.fill(repeat)(message))
    Future.successful(source)
  }

//  override def random(count: Int) = ServerServiceCall { _ =>
//    if (count < 1) {
////      Future.failed(NonPositiveIntegerException(count))
//      Future.failed(new RuntimeException(count.toString))
//    } else {
//      val numbers = Seq.fill(count)(Random.nextInt(10) + 1)
//      Future.successful(numbers)
//    }
//  }

//  override def tick(interval: Int) = ServerServiceCall { message =>
//    if (interval < 1) {
////      Future.failed(NonPositiveIntegerException(interval))
//      Future.failed(new RuntimeException(interval.toString))
//    } else {
//      val source = Source.tick(Duration.Zero, interval.milliseconds, message).mapMaterializedValue(_ => NotUsed)
//      Future.successful(source)
//    }
//  }

//  override def echo = ServerServiceCall { source =>
//    Future.successful(source)
//  }

//  override def binary = ServerServiceCall { _ =>
//    val bytes = Array.ofDim[Byte](16)
//    def nextByteString: ByteString = {
//      Random.nextBytes(bytes)
//      ByteString.apply(bytes)
//    }
//
//    val source = Source
//      .tick(Duration.Zero, 1.second, NotUsed)
//      .map(_ => nextByteString)
//      .mapMaterializedValue(_ => NotUsed)
//    Future.successful(source)
//  }

}
