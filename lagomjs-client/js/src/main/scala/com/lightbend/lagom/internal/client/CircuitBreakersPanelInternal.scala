package com.lightbend.lagom.internal.client

import akka.actor.ActorSystem
import com.lightbend.lagom.internal.spi.CircuitBreakerMetricsProvider

import scala.concurrent.Future

private[lagom] class CircuitBreakersPanelInternal(
    system: ActorSystem,
    circuitBreakerConfig: CircuitBreakerConfig,
    metricsProvider: CircuitBreakerMetricsProvider
) {
  def withCircuitBreaker[T](id: String)(body: => Future[T]): Future[T] = body
}

class CircuitBreakerConfig() {}
