package com.lightbend.lagom.internal.spi

trait ServiceDiscovery {
  def discoverServices(classLoader: ClassLoader): java.util.List[ServiceDescription]
}
