/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package play.api

/*
 * Copy of play.api.Mode removing the Java interoperability for JS compatibility.
 * https://github.com/playframework/playframework/blob/master/core/play/src/main/scala/play/api/Mode.scala
 */
sealed abstract class Mode

object Mode {

  case object Dev  extends Mode
  case object Test extends Mode
  case object Prod extends Mode

  lazy val values: Set[Mode] = Set(Dev, Test, Prod)
}
