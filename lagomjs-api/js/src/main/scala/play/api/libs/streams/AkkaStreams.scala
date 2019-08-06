/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs.streams

import akka.Done
import akka.stream.FlowShape
import akka.stream.scaladsl.Broadcast
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.GraphDSL
import akka.stream.scaladsl.Sink

import scala.concurrent.Future

/*
 * Copy of [[play.api.libs.AkkaStreams.ignoreAfterCancellation]] for JS compatibility.
 * Omitted all other objects in the source file.
 */
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
