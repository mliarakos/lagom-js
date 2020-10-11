package org.mliarakos.lagomjs.it.impl

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.lightbend.lagom.scaladsl.api.transport.Method
import com.lightbend.lagom.scaladsl.server.ServerServiceCall
import org.mliarakos.lagomjs.it.api.IntegrationTestService
import org.mliarakos.lagomjs.it.api.domain.Output
import org.mliarakos.lagomjs.it.api.exception.TestException
import org.mliarakos.lagomjs.it.test.TestValues

import scala.collection.immutable._
import scala.concurrent.Future

class IntegrationTestServiceImpl(implicit mat: Materializer) extends IntegrationTestService {

  private def validateMethod[Request, Response](method: Method)(serviceCall: ServerServiceCall[Request, Response]) =
    ServerServiceCall.compose { requestHeader =>
      assert(requestHeader.method == method, s"Expected method $method, got ${requestHeader.method}")
      serviceCall
    }

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

  override def testRestGetCall(a: String) = validateMethod(Method.GET) {
    ServerServiceCall { _ =>
      Future.successful(a)
    }
  }

  override def testRestPostCall = validateMethod(Method.POST) {
    ServerServiceCall { input =>
      val output = Output(input.a, input.b)
      Future.successful(output)
    }
  }

  override def testRestPutCall = validateMethod(Method.PUT) {
    ServerServiceCall { input =>
      val output = Output(input.a, input.b)
      Future.successful(output)
    }
  }

  override def testRestDeleteCall(a: String) = validateMethod(Method.DELETE) {
    ServerServiceCall { _ =>
      Future.successful(a)
    }
  }

  override def testRestHeadCall = validateMethod(Method.HEAD) {
    ServerServiceCall { _ =>
      Future.successful(NotUsed)
    }
  }

  override def testRestPatchCall(a: String) = validateMethod(Method.PATCH) {
    ServerServiceCall { input =>
      val output = Output(input.a, input.b)
      Future.successful(output)
    }
  }

  override def testStreamingRequest(limit: Int) = ServerServiceCall { source =>
    source.take(limit).runWith(Sink.seq)
  }

  override def testEmptyRequestBoundedStreamingResponse(start: Int, end: Int) = ServerServiceCall { _ =>
    val source = Source(Seq.range(start, end))
    Future.successful(source)
  }

  override def testRequestBoundedStreamingResponse(start: Int) = ServerServiceCall { end =>
    val source = Source(Seq.range(start, end))
    Future.successful(source)
  }

  override def testEmptyRequestUnboundedStreamingResponse(start: Int) = ServerServiceCall { _ =>
    val source = Source.unfold(start)(current => Some(current + 1, current))
    Future.successful(source)
  }

  override def testRequestUnboundedStreamingResponse = ServerServiceCall { start =>
    val source = Source.unfold(start)(current => Some(current + 1, current))
    Future.successful(source)
  }

  override def testBoundedStreaming(limit: Int) = ServerServiceCall { source =>
    Future.successful(source.take(limit))
  }

  override def testUnboundedStreaming = ServerServiceCall { source =>
    Future.successful(source)
  }

  override def testStreamingBinary(num: Int) = ServerServiceCall { byte =>
    val data   = Seq.fill(num)(ByteString(Array.fill(num)(byte)))
    val source = Source(data)
    Future.successful(source)
  }

  override def testException = ServerServiceCall { msg =>
    Future.failed(TestException(msg))
  }

}
