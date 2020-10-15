package com.lightbend.lagom.internal.scaladsl.client

import akka.stream.Materializer
import com.lightbend.lagom.internal.client.WebSocketClient
import com.lightbend.lagom.internal.client.WebSocketClientConfig

import scala.concurrent.ExecutionContext

private[lagom] class ScaladslWebSocketClient(config: WebSocketClientConfig)(
    implicit ec: ExecutionContext,
    materializer: Materializer
) extends WebSocketClient(config)(ec, materializer)
    with ScaladslServiceApiBridge
