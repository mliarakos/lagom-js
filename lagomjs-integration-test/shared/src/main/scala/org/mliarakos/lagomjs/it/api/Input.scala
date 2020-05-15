package org.mliarakos.lagomjs.it.api

import play.api.libs.json.Format
import play.api.libs.json.Json

case class Input(a: String, b: Int)

object Input {
  implicit val format: Format[Input] = Json.format
}
