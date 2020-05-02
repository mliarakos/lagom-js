package org.mliarakos.lagomjs.it.impl

import org.mliarakos.lagomjs.it.api.IntegrationTestService
import org.scalatest.AsyncWordSpec
import org.scalatest.Matchers

import scala.concurrent.ExecutionContext
import scala.scalajs.concurrent.JSExecutionContext

class IntegrationTestServiceSpec extends AsyncWordSpec with Matchers {

  implicit override val executionContext: ExecutionContext = JSExecutionContext.queue

  // Create application and client using the port of the test application
  private val port        = Config.port.getOrElse(throw new RuntimeException("Missing application port"))
  private val application = new IntegrationTestApplication(port, "localhost")
  private val client      = application.serviceClient.implement[IntegrationTestService]

  "The IntegrationTestService" should {
    "call the greeting endpoint" in {
      for {
        response <- client.greeting.invoke()
      } yield {
        response shouldBe "Welcome!"
      }
    }
  }

}
