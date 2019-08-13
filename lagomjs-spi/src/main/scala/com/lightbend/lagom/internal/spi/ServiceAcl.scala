package com.lightbend.lagom.internal.spi

import java.util.Optional

trait ServiceAcl {
  def method(): Optional[String]
  def pathPattern(): Optional[String]
}
