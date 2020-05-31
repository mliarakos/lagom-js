package org.mliarakos.lagomjs.it.test

import java.net.URI

import com.lightbend.lagom.scaladsl.client.StandaloneLagomClientFactory
import com.lightbend.lagom.scaladsl.client.StaticServiceLocatorComponents
import org.scalajs.dom.window

class IntegrationTestApplication(port: Int, hostname: String = window.location.hostname)
    extends StandaloneLagomClientFactory("integration-test-client")
    with StaticServiceLocatorComponents {
  override def staticServiceUri: URI = URI.create(s"http://$hostname:$port")
}
