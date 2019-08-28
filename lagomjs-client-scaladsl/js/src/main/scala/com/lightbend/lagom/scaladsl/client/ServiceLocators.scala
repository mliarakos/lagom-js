package com.lightbend.lagom.scaladsl.client

import java.net.URI
import java.util.concurrent.atomic.AtomicInteger

import com.lightbend.lagom.scaladsl.api.Descriptor.Call
import com.lightbend.lagom.scaladsl.api.Descriptor
import com.lightbend.lagom.scaladsl.api.LagomConfigComponent
import com.lightbend.lagom.scaladsl.api.ServiceLocator

import scala.collection.immutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait AbstractComponents extends LagomConfigComponent {
  def executionContext: ExecutionContext
}

abstract class AbstractServiceLocator(implicit ec: ExecutionContext) extends ServiceLocator {

  /**
   * Do the given block with the given service looked up.
   *
   * This is invoked by [[doWithService()]].
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

}

/**
 * Components for using the static service locator.
 */
trait StaticServiceLocatorComponents extends AbstractComponents {
  def staticServiceUri: URI

  lazy val serviceLocator: ServiceLocator = new StaticServiceLocator(staticServiceUri)(executionContext)
}

/**
 * A static service locator, that always resolves the same URI.
 */
class StaticServiceLocator(uri: URI)(implicit ec: ExecutionContext) extends AbstractServiceLocator {
  override def locate(name: String, serviceCall: Call[_, _]): Future[Option[URI]] = Future.successful(Some(uri))
}

/**
 * Components for using the round robin service locator.
 */
trait RoundRobinServiceLocatorComponents extends AbstractComponents {
  def roundRobinServiceUris: immutable.Seq[URI]

  lazy val serviceLocator: ServiceLocator = new RoundRobinServiceLocator(roundRobinServiceUris)(executionContext)
}

/**
 * A round robin service locator, that cycles through a list of URIs.
 */
class RoundRobinServiceLocator(uris: immutable.Seq[URI])(implicit ec: ExecutionContext) extends AbstractServiceLocator {

  private val counter = new AtomicInteger(0)

  override def locate(name: String, serviceCall: Call[_, _]): Future[Option[URI]] = {
    val index = Math.abs(counter.getAndIncrement() % uris.size)
    val uri   = uris(index)
    Future.successful(Some(uri))
  }

  override def locateAll(name: String, serviceCall: Call[_, _]): Future[List[URI]] =
    Future.successful(uris.toList)

}
