package com.lightbend.lagom.internal.spi

trait CircuitBreakerMetrics {
  def onOpen(): Unit
  def onClose(): Unit
  def onHalfOpen(): Unit
  def onCallSuccess(elapsedNanos: Long): Unit
  def onCallFailure(elapsedNanos: Long): Unit
  def onCallTimeoutFailure(elapsedNanos: Long): Unit
  def onCallBreakerOpenFailure(): Unit
  def stop(): Unit
}
