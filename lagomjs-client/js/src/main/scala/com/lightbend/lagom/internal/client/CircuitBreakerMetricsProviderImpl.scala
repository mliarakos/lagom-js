package com.lightbend.lagom.internal.client

import akka.actor.ActorSystem
import com.lightbend.lagom.internal.spi.CircuitBreakerMetrics
import com.lightbend.lagom.internal.spi.CircuitBreakerMetricsProvider

class CircuitBreakerMetricsProviderImpl(val system: ActorSystem) extends CircuitBreakerMetricsProvider {

  override def start(breakerId: String): CircuitBreakerMetrics = {
    new CircuitBreakerMetricsImpl(breakerId)
  }

}

class CircuitBreakerMetricsImpl(val breakerId: String) extends CircuitBreakerMetrics {

  override def onOpen(): Unit = {}

  override def onClose(): Unit = {}

  override def onHalfOpen(): Unit = {}

  override def onCallSuccess(elapsedNanos: Long): Unit = {}

  override def onCallFailure(elapsedNanos: Long): Unit = {}

  override def onCallTimeoutFailure(elapsedNanos: Long): Unit = {}

  override def onCallBreakerOpenFailure(): Unit = {}

  override def stop(): Unit = {}

}
