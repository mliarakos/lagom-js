/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.api

object HeaderUtils {

  /**
   * Normalize an HTTP header name.
   *
   * @param name the header name
   * @return the normalized header name
   */
  @inline
  def normalize(name: String): String = name.toLowerCase
}
