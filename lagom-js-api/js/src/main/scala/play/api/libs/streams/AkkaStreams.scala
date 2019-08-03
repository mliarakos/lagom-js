package play.api.libs.streams

/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

/*
 * Copy of [[play.api.libs.AkkaStreams.ignoreAfterCancellation]] for JS compatibility.
 * Omitted all other objects in the source file.
 */

import akka.Done
import akka.stream.FlowShape
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Sink}

import scala.concurrent.Future

object AkkaStreams {

  /**
   * A flow that will ignore downstream cancellation, and instead will continue receiving and ignoring the stream.
   */
  def ignoreAfterCancellation[T]: Flow[T, T, Future[Done]] = {
    Flow.fromGraph(GraphDSL.create(Sink.ignore) { implicit builder => ignore =>
      import GraphDSL.Implicits._
      // This pattern is an effective way to absorb cancellation, Sink.ignore will keep the broadcast always flowing
      // even after sink.inlet cancels.
      val broadcast = builder.add(Broadcast[T](2, eagerCancel = false))
      broadcast.out(0) ~> ignore.in
      FlowShape(broadcast.in, broadcast.out(1))
    })
  }

}
