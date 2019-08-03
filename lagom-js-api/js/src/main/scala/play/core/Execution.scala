package play.core

private[play] object Execution {
  def trampoline = scala.scalajs.concurrent.JSExecutionContext.queue
}
