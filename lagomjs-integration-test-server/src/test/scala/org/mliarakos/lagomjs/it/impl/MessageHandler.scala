package org.mliarakos.lagomjs.it.impl

import scala.annotation.tailrec
import scala.collection.immutable._
import scala.concurrent.Promise
import scala.concurrent.TimeoutException
import scala.concurrent.duration.Deadline
import scala.util.Try

/*
 * Derived from org.scalajs.jsenv.test.kit.MsgHandler
 * https://github.com/scala-js/scala-js/blob/master/js-envs-test-kit/src/main/scala/org/scalajs/jsenv/test/kit/MsgHandler.scala
 */
class MessageHandler {
  private[this] var messages: Queue[String] = Queue.empty[String]
  private[this] val run                     = Promise[Unit]

  def onMessage(msg: String): Unit = synchronized {
    if (run.isCompleted) {
      throw new IllegalStateException("run already completed but still got a message")
    }

    messages = messages.enqueue(msg)
    notifyAll()
  }

  def onRunComplete(t: Try[Unit]): Unit = synchronized {
    run.complete(t)
    notifyAll()
  }

  @tailrec
  final def waitOnMessage(deadline: Deadline): String = synchronized {
    if (messages.nonEmpty) {
      val (message, remaining) = messages.dequeue
      messages = remaining
      message
    } else if (run.isCompleted) {
      val cause = run.future.value.get.failed.getOrElse(null)
      throw new AssertionError("no messages left and run has completed", cause)
    } else {
      val millis = deadline.timeLeft.toMillis

      if (millis <= 0) {
        throw new TimeoutException("timed out waiting for next message")
      }

      wait(millis)
      waitOnMessage(deadline)
    }
  }

  /** @note may only be called once the run is completed. */
  def remainingMessages(): List[String] = synchronized(messages.toList)
}
