package org.mliarakos.lagomjs.it.impl

import com.lightbend.lagom.scaladsl.server.LagomApplication
import com.lightbend.lagom.scaladsl.server.LagomApplicationContext
import com.lightbend.lagom.scaladsl.server.LagomServer
import com.softwaremill.macwire._
import org.mliarakos.lagomjs.it.api.IntegrationTestService
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.EssentialFilter
import play.filters.cors.CORSComponents

import scala.collection.immutable._

abstract class IntegrationTestApplication(context: LagomApplicationContext)
    extends LagomApplication(context)
    with AhcWSComponents
    with CORSComponents {
  override val httpFilters: Seq[EssentialFilter] = Seq(corsFilter)
  override lazy val lagomServer: LagomServer     = serverFor[IntegrationTestService](wire[IntegrationTestServiceImpl])
}
