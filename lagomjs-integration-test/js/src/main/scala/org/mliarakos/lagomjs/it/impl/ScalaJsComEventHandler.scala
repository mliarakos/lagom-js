package org.mliarakos.lagomjs.it.impl

import org.mliarakos.lagomjs.it.api.TestMessage
import sbt.testing.Event
import sbt.testing.EventHandler
import sbt.testing.Status

class ScalaJsComEventHandler extends EventHandler {
  var result: String = TestMessage.SUCCEEDED

  override def handle(event: Event): Unit = {
    if (event.status() == Status.Failure) {
      result = TestMessage.FAILED
    }
    ScalajsCom.send(TestMessage.RUNNING)
  }
}
