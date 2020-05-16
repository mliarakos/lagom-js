package org.mliarakos.lagomjs.it.impl

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import org.mliarakos.lagomjs.it.api.Input
import org.mliarakos.lagomjs.it.api.IntegrationTestService
import org.mliarakos.lagomjs.it.api.Output
import org.mliarakos.lagomjs.it.api.TestValues
import org.scalatest.AsyncWordSpec
import org.scalatest.Matchers

import scala.collection.immutable._
import scala.concurrent.ExecutionContext
import scala.language.postfixOps
import scala.scalajs.concurrent.JSExecutionContext

class IntegrationTestServiceSpec extends AsyncWordSpec with Matchers {

  implicit override val executionContext: ExecutionContext = JSExecutionContext.queue

  // Create application and client using the port of the test application
  private val port        = Config.port.getOrElse(throw new RuntimeException("Missing application port"))
  private val application = new IntegrationTestApplication(port, "localhost")
  private val client      = application.serviceClient.implement[IntegrationTestService]

  private implicit val mat: Materializer = application.materializer

  private val DEFAULT = TestValues.DEFAULT
  private val A       = "a"
  private val B       = "b"
  private val NUM     = 2
  private val REPEAT  = 10

  "The IntegrationTestService" should {
    "invoke a call endpoint" in {
      for {
        response <- client.testCall.invoke()
      } yield {
        response shouldBe DEFAULT
      }
    }
    "invoke a namedCall endpoint" in {
      for {
        response <- client.testNamedCall.invoke()
      } yield {
        response shouldBe DEFAULT
      }
    }
    "invoke a pathCall endpoint" in {
      for {
        response <- client.testPathCall(A).invoke()
      } yield {
        response shouldBe A
      }
    }
    "invoke a pathCall endpoint with multiple parameters" in {
      for {
        response <- client.testPathCallMultiple(A, B).invoke()
      } yield {
        response shouldBe A + B
      }
    }
    "invoke a pathCall endpoint with a query parameter" in {
      for {
        response <- client.testPathCallQuery(A).invoke()
      } yield {
        response shouldBe A
      }
    }
    "invoke a pathCall endpoint with multiple query parameters" in {
      for {
        response <- client.testPathCallQueryMultiple(A, B).invoke()
      } yield {
        response shouldBe A + B
      }
    }
    "invoke a restCall GET endpoint" in {
      for {
        response <- client.testRestGetCall(A).invoke()
      } yield {
        response shouldBe A
      }
    }
    "invoke a restCall POST endpoint" in {
      val input = Input(A, NUM)
      for {
        response <- client.testRestPostCall.invoke(input)
      } yield {
        response shouldBe Output(A, NUM)
      }
    }
    "invoke a restCall PUT endpoint" in {
      val input = Input(A, NUM)
      for {
        response <- client.testRestPutCall.invoke(input)
      } yield {
        response shouldBe Output(A, NUM)
      }
    }
    "invoke a restCall DELETE endpoint" in {
      for {
        response <- client.testRestDeleteCall(A).invoke()
      } yield {
        response shouldBe A
      }
    }
    // TODO: HEAD
    // TODO: OPTIONS
    // TODO: PATCH
    "invoke an endpoint with a streaming request" in {
      val data   = Seq.fill(REPEAT)(A)
      val source = Source(data)
      for {
        response <- client.testStreamingRequest(REPEAT).invoke(source)
      } yield {
        response shouldBe data
      }
    }
    "invoke an endpoint with a streaming response" in {
      for {
        source <- client.testStreamingResponse(REPEAT).invoke(A)
        result <- source.runWith(Sink.seq)
      } yield {
        result shouldBe Seq.fill(REPEAT)(A)
      }
    }
    "invoke an endpoint with a streaming request and response" in {
      val data           = Seq.fill(REPEAT)(A)
      val outgoingSource = Source(data)
      for {
        incomingSource <- client.testStreaming(REPEAT).invoke(outgoingSource)
        result         <- incomingSource.runWith(Sink.seq)
      } yield {
        result shouldBe data
      }
    }
    "invoke an endpoint with a streaming binary response" in {
      val byte = NUM.toByte
      for {
        source <- client.testStreamingBinary(REPEAT).invoke(byte)
        result <- source.runWith(Sink.seq)
      } yield {
        val expected = Seq.fill(REPEAT)(Array.fill(REPEAT)(byte))
        (result.map(_.toArray) should contain).theSameElementsInOrderAs(expected)
      }
    }
  }
}
