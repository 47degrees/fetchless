package fetchless.debug

import munit.CatsEffectSuite
import fetchless.Fetch
import cats.effect.IO
import fetchless.syntax._
import cats.syntax.all._
import cats.data.Chain
import cats.Applicative

final class DebugSpec extends CatsEffectSuite {
  test("Debug in sequence") {
    val fetch = Fetch.echo[IO, Int]("intFetch")

    DebugFetch.wrap(fetch).flatMap { debugFetch =>
      implicit val f = debugFetch

      val program = 1.fetch(debugFetch) >>
        2.fetchDedupe(debugFetch) >>
        3.fetchLazy(Applicative[IO], debugFetch).run >>
        List(4).fetchAll(debugFetch) >>
        List(5).fetchAllDedupe(debugFetch) >>
        List(6).fetchAllLazy(debugFetch).run.void

      val expectedTypeChain = Chain(
        DebugLog.FetchType.Fetch[Int](1),
        DebugLog.FetchType.FetchDedupe[Int](2),
        DebugLog.FetchType.FetchDedupe[Int](3),
        DebugLog.FetchType.FetchBatch[Int](Set(4)),
        DebugLog.FetchType.FetchBatchDedupe[Int](Set(5)),
        DebugLog.FetchType.FetchBatchDedupe[Int](Set(6))
      )

      for {
        _           <- program
        beforeFlush <- debugFetch.getDebugLogs
        _           <- IO(assertEquals(beforeFlush.map(_.fetchType), expectedTypeChain))
        _           <- debugFetch.flushLogs.assertEquals(beforeFlush)
        _           <- debugFetch.getDebugLogs.assertEquals(Chain.empty)
      } yield ()
    }
  }
}
