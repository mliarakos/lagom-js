package com.lightbend.lagom.internal.spi

trait ServiceDescription {
  def name(): String
  def acls(): java.util.List[ServiceAcl]
}
