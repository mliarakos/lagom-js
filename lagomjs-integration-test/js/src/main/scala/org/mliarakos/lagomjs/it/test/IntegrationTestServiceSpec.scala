package org.mliarakos.lagomjs.it.test

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import com.lightbend.lagom.scaladsl.api.transport.Method
import com.lightbend.lagom.scaladsl.api.transport.RequestHeader
import org.mliarakos.lagomjs.it.api._
import org.mliarakos.lagomjs.it.api.domain.Input
import org.mliarakos.lagomjs.it.api.domain.Output
import org.mliarakos.lagomjs.it.api.exception.TestException
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import scala.collection.immutable._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
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

  private def unboundedSource(message: String) = {
    Source.tick(Duration.Zero, 100.milliseconds, message).mapMaterializedValue(_ => NotUsed)
  }

  private def validateMethod(method: Method)(requestHeader: RequestHeader): RequestHeader = {
    assert(requestHeader.method == method)
    requestHeader
  }

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
        response <- client
          .testRestGetCall(A)
          .handleRequestHeader(validateMethod(Method.GET))
          .invoke()
      } yield {
        response shouldBe A
      }
    }
    "invoke a restCall POST endpoint" in {
      val input = Input(A, NUM)
      for {
        response <- client.testRestPostCall
          .handleRequestHeader(validateMethod(Method.POST))
          .invoke(input)
      } yield {
        response shouldBe Output(A, NUM)
      }
    }
    "invoke a restCall PUT endpoint" in {
      val input = Input(A, NUM)
      for {
        response <- client.testRestPutCall
          .handleRequestHeader(validateMethod(Method.PUT))
          .invoke(input)
      } yield {
        response shouldBe Output(A, NUM)
      }
    }
    "invoke a restCall DELETE endpoint" in {
      for {
        response <- client
          .testRestDeleteCall(A)
          .handleRequestHeader(validateMethod(Method.DELETE))
          .invoke()
      } yield {
        response shouldBe A
      }
    }
    "invoke a restCall HEAD endpoint" in {
      for {
        response <- client.testRestHeadCall
          .handleRequestHeader(validateMethod(Method.HEAD))
          .invoke()
      } yield {
        response shouldBe NotUsed
      }
    }
    "invoke a restCall PATCH endpoint" in {
      val input = Input(A, NUM)
      for {
        response <- client
          .testRestPatchCall(A)
          .handleRequestHeader(validateMethod(Method.PATCH))
          .invoke(input)
      } yield {
        response shouldBe Output(A, NUM)
      }
    }
    "invoke an endpoint with a streaming request" in {
      val source = unboundedSource(A)
      for {
        response <- client.testStreamingRequest(REPEAT).invoke(source)
      } yield {
        response shouldBe Seq.fill(REPEAT)(A)
      }
    }
    "invoke an endpoint with a bounded streaming response" in {
      for {
        source <- client.testBoundedStreamingResponse(REPEAT).invoke(A)
        result <- source.runWith(Sink.seq)
      } yield {
        result shouldBe Seq.fill(REPEAT)(A)
      }
    }
    "invoke an endpoint with an unbounded streaming response" in {
      for {
        source <- client.testUnboundedStreamingResponse.invoke(A)
        result <- source.take(REPEAT).runWith(Sink.seq)
      } yield {
        result shouldBe Seq.fill(REPEAT)(A)
      }
    }
    "invoke an endpoint with a bounded streaming request and response" in {
      val outgoingSource = unboundedSource(A)
      for {
        incomingSource <- client.testBoundedStreaming(REPEAT).invoke(outgoingSource)
        result         <- incomingSource.runWith(Sink.seq)
      } yield {
        result shouldBe Seq.fill(REPEAT)(A)
      }
    }
    "invoke an endpoint with an unbounded streaming request and response" in {
      val outgoingSource = unboundedSource(A)
      for {
        incomingSource <- client.testUnboundedStreaming.invoke(outgoingSource)
        result         <- incomingSource.take(REPEAT).runWith(Sink.seq)
      } yield {
        result shouldBe Seq.fill(REPEAT)(A)
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
    "invoke an endpoint that uses a custom exception" in {
      for {
        ex <- recoverToExceptionIf[TestException](client.testException.invoke(A))
      } yield {
        ex.exceptionMessage.detail shouldBe A
      }
    }
  }
}
