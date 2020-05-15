package org.mliarakos.lagomjs.it.api

import play.api.libs.json.Format
import play.api.libs.json.Json

case class Output(a: String, b: Int)

object Output {
  implicit val format: Format[Output] = Json.format
}
