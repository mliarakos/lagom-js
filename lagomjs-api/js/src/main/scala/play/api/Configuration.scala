/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package play.api

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

/*
 * Skeleton of play.api.Configuration removing all functionality for JS compatibility.
 */
case class Configuration(underlying: Config)

object Configuration {
  def empty = Configuration(ConfigFactory.empty())
}
