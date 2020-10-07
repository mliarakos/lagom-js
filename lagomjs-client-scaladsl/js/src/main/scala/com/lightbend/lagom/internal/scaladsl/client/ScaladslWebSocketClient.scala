package com.lightbend.lagom.internal.scaladsl.client

import akka.stream.Materializer
import com.lightbend.lagom.internal.client.WebSocketClient
import com.typesafe.config.Config

import scala.concurrent.ExecutionContext

private[lagom] class ScaladslWebSocketClient(config : Config)(implicit ec: ExecutionContext, materializer: Materializer)
    extends WebSocketClient(config)(ec, materializer)
    with ScaladslServiceApiBridge
