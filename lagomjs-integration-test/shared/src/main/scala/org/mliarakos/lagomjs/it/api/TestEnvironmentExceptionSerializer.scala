package org.mliarakos.lagomjs.it.api

import com.lightbend.lagom.scaladsl.api.deser.DefaultExceptionSerializer
import com.lightbend.lagom.scaladsl.api.transport.ExceptionMessage
import com.lightbend.lagom.scaladsl.api.transport.TransportErrorCode
import com.lightbend.lagom.scaladsl.api.transport.TransportException
import play.api.Environment
import play.api.Mode

import scala.collection.immutable._

class TestEnvironmentExceptionSerializer(environment: Environment) extends DefaultExceptionSerializer(environment) {
  private val exceptions: Map[String, (TransportErrorCode, ExceptionMessage) => TransportException] = Map(
    TestException.name -> TestException.apply
  )

  protected override def fromCodeAndMessage(
      transportErrorCode: TransportErrorCode,
      exceptionMessage: ExceptionMessage
  ): Throwable = {
    exceptions
      .getOrElse(exceptionMessage.name, super.fromCodeAndMessage _)
      .apply(transportErrorCode, exceptionMessage)
  }
}

object TestEnvironmentExceptionSerializer {
  val devEnvironment: Environment = Environment.simple(mode = Mode.Dev)
}
