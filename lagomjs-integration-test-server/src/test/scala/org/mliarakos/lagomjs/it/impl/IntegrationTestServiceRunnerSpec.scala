package org.mliarakos.lagomjs.it.impl

import java.nio.file.Paths

import com.lightbend.lagom.scaladsl.server.LocalServiceLocator
import com.lightbend.lagom.scaladsl.testkit.ServiceTest
import org.mliarakos.lagomjs.it.test.TestMessage
import org.openqa.selenium.chrome.ChromeOptions
import org.scalajs.jsenv.Input.Script
import org.scalajs.jsenv.RunConfig
import org.scalajs.jsenv.selenium.FileMaterializer
import org.scalajs.jsenv.selenium.SeleniumJSEnv
import org.scalajs.logging.ScalaConsoleLogger
import org.scalatest.TryValues._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import scala.annotation.tailrec
import scala.collection.immutable._
import scala.concurrent.duration._
import scala.util.Try

class IntegrationTestServiceRunnerSpec extends AsyncWordSpec with Matchers {

  private val timeout = 30.seconds
  private val filename =
    "./lagomjs-integration-test-server/target/web/web-modules/test/webjars/lib/lagomjs-integration-test-server/lagomjs-integration-test-fastopt.js"

  private def receive(handler: MessageHandler): Try[Boolean] = {
    @tailrec
    def process(): Boolean = handler.waitOnMessage(timeout.fromNow) match {
      case TestMessage.STARTED | TestMessage.RUNNING => process()
      case TestMessage.SUCCEEDED                     => true
      case TestMessage.FAILED                        => false
      case msg                                       => throw new RuntimeException(s"Received unexpected test message: $msg")
    }

    val result = Try(process())
    handler.onRunComplete(result.map(_ => ()))

    result
  }

  "The IntegrationTestService" should {
    "complete JavaScript tests" in ServiceTest.withServer(ServiceTest.defaultSetup) { ctx =>
      new IntegrationTestApplication(ctx) with LocalServiceLocator
    } { server =>
      // Get the port of the test application
      val port = server.playServer.httpPort.get

      // Create JavaScript execution environment
      val capabilities = new ChromeOptions().setHeadless(true)
      val config       = SeleniumJSEnv.Config() //.withKeepAlive(true)
      val jsEnv        = new SeleniumJSEnv(capabilities, config)

      // Create script input of the test suite
      val suite = Script(Paths.get(filename))

      // Create script input of code to start the test runner
      val code         = s"IntegrationTestRunner.run($port);"
      val materializer = FileMaterializer(config.materialization)
      val url          = materializer.materialize("run.js", code)
      val runner       = Script(Paths.get(url.toURI))

      // Start the JavaScript run with a communication channel
      // Use the MessageHandler to receive messages from the test runner
      val runConfig = RunConfig().withLogger(new ScalaConsoleLogger())
      val handler   = new MessageHandler()
      val run       = jsEnv.startWithCom(Seq(suite, runner), runConfig, handler.onMessage)

      // Receive and process messages from the JavaScript run
      // Stop the run after determining the result
      val result = receive(handler)
      run.close()

      // Wait for the run to completely stop then clean up and check the result
      for {
        _ <- run.future
      } yield {
        materializer.close()
        assert(result.success.value, "(JavaScript tests failed, examine the JavaScript testing output)")
      }
    }
  }

}
