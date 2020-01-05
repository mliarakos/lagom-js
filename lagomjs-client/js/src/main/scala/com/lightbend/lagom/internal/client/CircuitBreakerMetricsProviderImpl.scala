package com.lightbend.lagom.internal.client

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

import akka.actor.ActorSystem
import com.lightbend.lagom.internal.spi.CircuitBreakerMetrics
import com.lightbend.lagom.internal.spi.CircuitBreakerMetricsProvider

class CircuitBreakerMetricsProviderImpl(val system: ActorSystem) extends CircuitBreakerMetricsProvider {
  private val metrics = new CopyOnWriteArrayList[CircuitBreakerMetricsImpl]

  override def start(breakerId: String): CircuitBreakerMetrics = {
    val m = new CircuitBreakerMetricsImpl(breakerId, this)
    metrics.add(m)
    m
  }

  private[lagom] def remove(m: CircuitBreakerMetricsImpl): Unit =
    metrics.remove(m)

  private[lagom] def allMetrics(): java.util.List[CircuitBreakerMetricsImpl] =
    metrics
}

object CircuitBreakerMetricsImpl {
  final val Closed   = "closed"
  final val Open     = "open"
  final val HalfOpen = "half-open"
}

class CircuitBreakerMetricsImpl(val breakerId: String, provider: CircuitBreakerMetricsProviderImpl)
    extends CircuitBreakerMetrics {
  import CircuitBreakerMetricsImpl._

  private val log        = org.scalajs.dom.console
  private val stateValue = new AtomicReference[String](Closed)

  override def onOpen(): Unit = {
    stateValue.compareAndSet(Closed, Open)
    stateValue.compareAndSet(HalfOpen, Open)
    log.warn(s"Circuit breaker [${breakerId}] open")
  }

  override def onClose(): Unit = {
    stateValue.compareAndSet(Open, Closed)
    stateValue.compareAndSet(HalfOpen, Closed)
    log.info(s"Circuit breaker [${breakerId}] closed")
  }

  override def onHalfOpen(): Unit = {
    stateValue.compareAndSet(Open, HalfOpen)
    log.info(s"Circuit breaker [${breakerId}] half-open")
  }

  override def onCallSuccess(elapsedNanos: Long): Unit = {
    // TODO: implement
  }

  override def onCallFailure(elapsedNanos: Long): Unit = {
    // TODO: implement
  }

  override def onCallTimeoutFailure(elapsedNanos: Long): Unit = {
    // TODO: implement
  }

  override def onCallBreakerOpenFailure(): Unit = {
    // TODO: implement
  }

  override def stop(): Unit = {
    // TODO: implement
    provider.remove(this)
  }
}
