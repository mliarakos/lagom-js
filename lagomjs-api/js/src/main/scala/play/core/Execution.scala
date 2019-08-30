/*
 * Copyright (C) 2009-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package play.core

/*
 * Implementation of play.core.Execution.trampoline using the scalajs queue for JS compatibility.
 * https://github.com/playframework/playframework/blob/master/core/play/src/main/scala/play/core/Execution.scala
 */

private[play] object Execution {
  def trampoline = scala.scalajs.concurrent.JSExecutionContext.queue
}
