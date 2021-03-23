/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package play.api

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import scala.collection.JavaConverters._

/*
 * Skeleton of play.api.Configuration removing all functionality for JS compatibility.
 */
case class Configuration(underlying: Config)

object Configuration {
  def load(directSettings: Map[String, Any], defaultConfig: Config): Configuration = {
    // Prevent one level of config nesting as a temporary partial work around to:
    // https://github.com/akka-js/shocon/issues/55
    val combinedConfig = if (directSettings.nonEmpty) {
      val directConfig = ConfigFactory.parseMap(directSettings.asJava)
      directConfig.withFallback(defaultConfig)
    } else {
      defaultConfig
    }
    val resolvedConfig = ConfigFactory.load(combinedConfig)

    Configuration(resolvedConfig)
  }

  def empty = Configuration(ConfigFactory.empty())
}
