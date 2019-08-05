package com.lightbend.lagom.scaladsl.client

import java.net.URI

import com.lightbend.lagom.scaladsl.api.Descriptor.Call
import com.lightbend.lagom.scaladsl.api.{ Descriptor, ServiceLocator }

import scala.concurrent.{ ExecutionContext, Future }

/**
 * Components for using the static service locator.
 */
trait StaticServiceLocatorComponents {
  def executionContext: ExecutionContext
  def staticServiceUri: URI

  lazy val serviceLocator: ServiceLocator = new StaticServiceLocator(staticServiceUri)(executionContext)
}

/**
 * A static service locator, that always resolves the same URI.
 */
class StaticServiceLocator(uri: URI)(implicit ec: ExecutionContext) extends ServiceLocator {

  /**
   * Do the given block with the given service looked up.
   *
   * This is invoked by [[doWithService()]], after wrapping the passed in block
   * in a circuit breaker if configured to do so.
   *
   * The default implementation just delegates to the [[locate()]] method, but this method
   * can be overridden if the service locator wants to inject other behaviour after the service call is complete.
   *
   * @param name        The service name.
   * @param serviceCall The service call that needs the service lookup.
   * @param block       A block of code that will use the looked up service, typically, to make a call on that service.
   * @return A future of the result of the block, if the service lookup was successful.
   */
  protected def doWithServiceImpl[T](name: String, serviceCall: Descriptor.Call[_, _])(
      block: URI => Future[T]
  ): Future[Option[T]] = {
    locate(name, serviceCall).flatMap {
      case (Some(uri)) => block(uri).map(Some.apply)
      case None        => Future.successful(None)
    }
  }

  final override def doWithService[T](name: String, serviceCall: Call[_, _])(
      block: (URI) => Future[T]
  )(implicit ec: ExecutionContext): Future[Option[T]] = {
    doWithServiceImpl(name, serviceCall)(block)
  }

  override def locate(name: String, serviceCall: Call[_, _]): Future[Option[URI]] = Future.successful(Some(uri))
}
