package org.mliarakos.lagomjs.it.impl

import org.scalajs.dom.console
import sbt.testing.Logger

object JsConsoleLogger extends Logger {
  override def ansiCodesSupported(): Boolean = true
  override def error(msg: String): Unit      = console.error(msg)
  override def warn(msg: String): Unit       = console.log(msg)
  override def info(msg: String): Unit       = console.log(msg)
  override def debug(msg: String): Unit      = console.log(msg)
  override def trace(t: Throwable): Unit     = console.error(t.toString)
}
