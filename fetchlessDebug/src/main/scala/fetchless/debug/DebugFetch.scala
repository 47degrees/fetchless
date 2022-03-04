package fetchless.debug

import fetchless.Fetch
import cats.effect.Concurrent
import fetchless.DedupedFetch
import fetchless.DedupedFetch
import fetchless.CacheMap
import fetchless.LazyFetch
import cats.data.Chain
import cats.syntax.all._
import cats.effect.Clock

/** A `Fetch` instance that produces a concurrently accessible log of operations. */
trait DebugFetch[F[_], I, A] extends Fetch[F, I, A] {

  /** Gets the current chain of debug logs */
  def getDebugLogs: F[Chain[DebugLog[I]]]

  /** Gets the current chain of debug logs and then resets the chain */
  def flushLogs: F[Chain[DebugLog[I]]]

  /** Resets the chain of logs without requesting them */
  def reset: F[Unit]
}

object DebugFetch {
  def wrap[F[_]: Concurrent: Clock, I, A](fetch: Fetch[F, I, A]): F[DebugFetch[F, I, A]] =
    Concurrent[F].ref(Chain.empty[DebugLog[I]]).map { ref =>
      new DebugFetch[F, I, A] {

        private def updateAndDo[A](action: F[A])(fetchType: DebugLog.FetchType[I]) = {
          Clock[F].timed(action).flatMap { case (time, a) =>
            val log = DebugLog(fetch.id, time, fetchType)
            ref.update(_ :+ log).as(a)
          }
        }
        val id: String = fetch.id

        def single(i: I): F[Option[A]] = updateAndDo(fetch.single(i))(DebugLog.FetchType.Fetch(i))

        def singleDedupe(i: I): F[DedupedFetch[F, Option[A]]] =
          updateAndDo(fetch.singleDedupe(i))(DebugLog.FetchType.FetchDedupe(i))

        def singleDedupeCache(i: I)(cache: CacheMap): F[DedupedFetch[F, Option[A]]] =
          updateAndDo(fetch.singleDedupeCache(i)(cache))(DebugLog.FetchType.FetchDedupe(i))

        def singleLazyWrap[B](i: I)(
            f: F[DedupedFetch[F, Option[A]]] => F[DedupedFetch[F, B]]
        ): LazyFetch[F, B] =
          fetch.singleLazyWrap(i) { foa =>
            updateAndDo(f(foa))(DebugLog.FetchType.FetchLazy(i))
          }

        def batch(iSet: Set[I]): F[Map[I, A]] =
          updateAndDo(fetch.batch(iSet))(DebugLog.FetchType.FetchBatch(iSet))

        def batchDedupe(iSet: Set[I]): F[DedupedFetch[F, Map[I, A]]] =
          updateAndDo(fetch.batchDedupe(iSet))(DebugLog.FetchType.FetchBatchDedupe(iSet))

        def batchDedupeCache(is: Set[I])(cache: CacheMap): F[DedupedFetch[F, Map[I, A]]] =
          updateAndDo(fetch.batchDedupeCache(is)(cache))(DebugLog.FetchType.FetchBatchDedupe(is))

        def batchLazyWrap[B](iSet: Set[I])(
            f: F[DedupedFetch[F, Map[I, A]]] => F[DedupedFetch[F, B]]
        ): LazyFetch[F, B] =
          fetch.batchLazyWrap(iSet) { fmap =>
            updateAndDo(f(fmap))(DebugLog.FetchType.FetchBatchLazy(iSet))
          }

        def getDebugLogs: F[Chain[DebugLog[I]]] = ref.get

        def flushLogs: F[Chain[DebugLog[I]]] = ref.modify(c => (Chain.empty, c))

        def reset: F[Unit] = ref.set(Chain.empty)

      }
    }
}
