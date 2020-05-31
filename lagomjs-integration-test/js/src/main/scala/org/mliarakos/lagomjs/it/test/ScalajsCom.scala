package org.mliarakos.lagomjs.it.test

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

@js.native
@JSGlobal("scalajsCom")
object ScalajsCom extends js.Object {
  def init(onReceive: js.Function1[String, Unit]): Unit = js.native
  def send(msg: String): Unit                           = js.native
}
