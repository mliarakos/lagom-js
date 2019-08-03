package play.api

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

/*
 * Skeleton of [[play.api.Configuration]] removing all functionality for JS compatibility.
 */
object Configuration {

  /**
   * Returns an empty Configuration object.
   */
  def empty = Configuration(ConfigFactory.empty())

}

case class Configuration(underlying: Config)
