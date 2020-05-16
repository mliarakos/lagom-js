package org.mliarakos.lagomjs.it.impl

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.lightbend.lagom.scaladsl.server.ServerServiceCall
import org.mliarakos.lagomjs.it.api.IntegrationTestService
import org.mliarakos.lagomjs.it.api.Output
import org.mliarakos.lagomjs.it.api.TestValues

import scala.collection.immutable._
import scala.concurrent.Future

class IntegrationTestServiceImpl(implicit mat: Materializer) extends IntegrationTestService {

  override def testCall = ServerServiceCall { _ =>
    Future.successful(TestValues.DEFAULT)
  }

  override def testNamedCall = ServerServiceCall { _ =>
    Future.successful(TestValues.DEFAULT)
  }

  override def testPathCall(a: String) = ServerServiceCall { _ =>
    Future.successful(a)
  }

  override def testPathCallMultiple(a: String, b: String) = ServerServiceCall { _ =>
    Future.successful(a + b)
  }

  override def testPathCallQuery(a: String) = ServerServiceCall { _ =>
    Future.successful(a)
  }

  override def testPathCallQueryMultiple(a: String, b: String) = ServerServiceCall { _ =>
    Future.successful(a + b)
  }

  override def testRestGetCall(a: String) = ServerServiceCall { _ =>
    Future.successful(a)
  }

  override def testRestPostCall = ServerServiceCall { input =>
    val output = Output(input.a, input.b)
    Future.successful(output)
  }

  override def testRestPutCall = ServerServiceCall { input =>
    val output = Output(input.a, input.b)
    Future.successful(output)
  }

  override def testRestDeleteCall(a: String) = ServerServiceCall { _ =>
    Future.successful(a)
  }

  override def testStreamingRequest(num: Int) = ServerServiceCall { source =>
    source.take(num).runWith(Sink.seq)
  }

  override def testStreamingResponse(num: Int) = ServerServiceCall { message =>
    val source = Source(Seq.fill(num)(message))
    Future.successful(source)
  }

  override def testStreaming(num: Int) = ServerServiceCall { source =>
    Future.successful(source.take(num))
  }

  override def testStreamingBinary(num: Int) = ServerServiceCall { byte =>
    val data   = Seq.fill(num)(ByteString(Array.fill(num)(byte)))
    val source = Source(data)
    Future.successful(source)
  }
}
