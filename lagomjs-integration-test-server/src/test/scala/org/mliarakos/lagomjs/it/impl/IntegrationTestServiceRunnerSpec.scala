package org.mliarakos.lagomjs.it.impl

import java.io.File

import com.lightbend.lagom.scaladsl.server.LocalServiceLocator
import com.lightbend.lagom.scaladsl.testkit.ServiceTest
import org.mliarakos.lagomjs.it.api.TestMessage
import org.openqa.selenium.chrome.ChromeOptions
import org.scalajs.core.tools.io.FileVirtualJSFile
import org.scalajs.core.tools.io.MemVirtualJSFile
import org.scalajs.core.tools.jsdep.ResolvedJSDependency
import org.scalajs.core.tools.logging.ScalaConsoleLogger
import org.scalajs.jsenv.ComJSRunner
import org.scalajs.jsenv.ConsoleJSConsole
import org.scalajs.jsenv.selenium.SeleniumJSEnv
import org.scalatest.AsyncWordSpec
import org.scalatest.Matchers

import scala.collection.immutable._
import scala.concurrent.Future
import scala.concurrent.duration._

class IntegrationTestServiceRunnerSpec extends AsyncWordSpec with Matchers {

  private val timeout = 30.seconds
  private val filename =
    "./lagomjs-integration-test-server/target/web/web-modules/test/webjars/lib/lagomjs-integration-test-server/lagomjs-integration-test-opt.js"

  @scala.annotation.tailrec
  private def awaitTest(runner: ComJSRunner): Boolean = {
    runner.receive(timeout) match {
      case TestMessage.STARTED | TestMessage.RUNNING => awaitTest(runner)
      case TestMessage.SUCCEEDED                     => true
      case TestMessage.FAILED                        => false
      case msg                                       => throw new RuntimeException(s"Received unexpected test message: $msg")
    }

  }

  "The IntegrationTestService" should {
    "complete JavaScript tests" in ServiceTest.withServer(ServiceTest.defaultSetup) { ctx =>
      new IntegrationTestApplication(ctx) with LocalServiceLocator
    } { server =>
      // Get the port of the test application
      val port = server.playServer.httpPort.get

      val capabilities = new ChromeOptions().setHeadless(true)
      val config       = SeleniumJSEnv.Config() //.withKeepAlive(true)
      val jsEnv        = new SeleniumJSEnv(capabilities, config)

      val suiteFile   = new File(filename)
      val suiteJsFile = FileVirtualJSFile(suiteFile)
      val suiteJsDep  = ResolvedJSDependency.minimal(suiteJsFile)

      val runCode   = s"IntegrationTestServiceSpec.run($port);"
      val runJsFile = new MemVirtualJSFile("run.js").withContent(runCode)

      val runner = jsEnv.comRunner(Seq(suiteJsDep), runJsFile)
      val run    = runner.start(new ScalaConsoleLogger(), ConsoleJSConsole)

      for {
        result <- Future(awaitTest(runner))
        _      <- Future(runner.close())
        _      <- run
      } yield assert(result, "(JavaScript tests failed, examine the JavaScript testing output)")
    }
  }

}
