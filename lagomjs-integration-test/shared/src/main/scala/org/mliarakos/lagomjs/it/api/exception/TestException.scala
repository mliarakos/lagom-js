package org.mliarakos.lagomjs.it.api.exception

import com.lightbend.lagom.scaladsl.api.transport.ExceptionMessage
import com.lightbend.lagom.scaladsl.api.transport.TransportErrorCode
import com.lightbend.lagom.scaladsl.api.transport.TransportException

case class TestException(
    override val errorCode: TransportErrorCode,
    override val exceptionMessage: ExceptionMessage
) extends TransportException(errorCode, exceptionMessage)

object TestException {
  val name: String             = classOf[TestException].getSimpleName
  val code: TransportErrorCode = TransportErrorCode.BadRequest

  def apply(msg: String): TestException = TestException(code, new ExceptionMessage(name, msg))
}
