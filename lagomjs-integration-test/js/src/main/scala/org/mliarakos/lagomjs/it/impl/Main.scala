package org.mliarakos.lagomjs.it.impl

import org.mliarakos.lagomjs.it.api.TestMessage
import org.scalajs.dom.console
import org.scalatest.tools.MasterRunner
import sbt.testing._

import scala.scalajs.js.annotation.JSExport
import scala.scalajs.js.annotation.JSExportTopLevel

@JSExportTopLevel("IntegrationTestServiceSpec")
object Main {
  @JSExport
  def run(port: Int): Unit = {
    // Signal testing has started
    ScalajsCom.send(TestMessage.STARTED)

    // Set the port to be used by the test suite
    // The test suite is created by the runner without arguments so the port can't be passed directly to the suite
    Config.port = Some(port)

    // Run basic SBT test task
    // The handler will signal that the test is running on each test event and track the final test result
    // Signal testing is done when the test completes
    val name     = classOf[IntegrationTestServiceSpec].getName
    val taskDef  = new TaskDef(name, new Fingerprint {}, true, Array(new SuiteSelector()))
    val runner   = new MasterRunner(Array.empty, Array.empty, new ClassLoader {})
    val task     = runner.tasks(Array(taskDef)).head
    val handler  = new ScalaJsComEventHandler()
    val complete = (_: Array[Task]) => { console.log(runner.done()); ScalajsCom.send(handler.result) }

    task.execute(handler, Array(JsConsoleLogger), complete)
  }
}