package lagomjs

import com.typesafe.config.ConfigFactory

object Config {
  val default: com.typesafe.config.Config = ConfigFactory.load()
}
