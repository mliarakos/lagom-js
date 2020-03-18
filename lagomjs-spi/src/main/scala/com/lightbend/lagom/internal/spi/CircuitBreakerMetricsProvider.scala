package com.lightbend.lagom.internal.spi

trait CircuitBreakerMetricsProvider {
  def start(breakerId: String): CircuitBreakerMetrics
}
