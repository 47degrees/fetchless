package fetchless.debug

import fetchless.Fetch
import cats.effect.Concurrent
import fetchless.DedupedRequest
import fetchless.DedupedRequest
import fetchless.FetchCache
import fetchless.LazyRequest
import cats.data.Chain
import cats.syntax.all._
import cats.effect.Clock
import cats.data.Kleisli
import fetchless.AllFetch
import cats.Applicative
import cats.effect.kernel.Ref

/** A `Fetch` instance that produces a concurrently accessible log of operations. */
abstract class DebugFetch[F[_]: Concurrent: Clock, I, A](
    fetch: Fetch[F, I, A],
    ref: Ref[F, Chain[DebugLog[I]]]
) extends Fetch[F, I, A] {

  protected def updateAndDo[A](action: F[A])(fetchType: DebugLog.FetchType[I]) = {
    Clock[F].timed(action).flatMap { case (time, a) =>
      val log = DebugLog(fetch.id, time, fetchType)
      ref.update(_ :+ log).as(a)
    }
  }
  val id: String = fetch.id

  val timer = fetch.timer

  def single(i: I): F[Option[A]] = updateAndDo(fetch.single(i))(DebugLog.FetchType.Fetch(i))

  def singleDedupe(i: I): F[DedupedRequest[F, Option[A]]] =
    updateAndDo(fetch.singleDedupe(i))(DebugLog.FetchType.FetchDedupe(i))

  def singleDedupeCache(i: I)(cache: FetchCache): F[DedupedRequest[F, Option[A]]] =
    updateAndDo(fetch.singleDedupeCache(i)(cache))(DebugLog.FetchType.FetchDedupe(i))

  def singleLazy(i: I): LazyRequest[F, Option[A]] = LazyRequest(
    Kleisli { c =>
      LazyRequest.ReqInfo
        .fetch[F, Option[A]](
          prevCache = c,
          fetchId = wrappedId,
          reqId = i,
          isBatch = false,
          doSingle = (s, sCache) =>
            singleDedupeCache(s.asInstanceOf[I])(sCache)
              .asInstanceOf[F[DedupedRequest[F, Any]]],
          doBatch = (b, bCache) =>
            batchDedupeCache(b.asInstanceOf[Set[I]])(bCache)
              .asInstanceOf[F[DedupedRequest[F, Map[Any, Any]]]],
          getResultK = Kleisli[F, FetchCache, DedupedRequest[F, Option[A]]] { kCache =>
            DedupedRequest
              .prepopulated[F](kCache)
              .copy(last = kCache.get(this)(i) match {
                case FetchCache.GetValueResult.ValueExists(v) => v.some
                case _                                        => none
              })
              .pure[F]
          },
          mapTo = _.asInstanceOf[DedupedRequest[F, Option[A]]]
        )
        .pure[F]
    }
  )

  def batch(iSet: Set[I]): F[Map[I, A]] =
    updateAndDo(fetch.batch(iSet))(DebugLog.FetchType.FetchBatch(iSet))

  def batchDedupe(iSet: Set[I]): F[DedupedRequest[F, Map[I, A]]] =
    updateAndDo(fetch.batchDedupe(iSet))(DebugLog.FetchType.FetchBatchDedupe(iSet))

  def batchDedupeCache(is: Set[I])(cache: FetchCache): F[DedupedRequest[F, Map[I, A]]] =
    updateAndDo(fetch.batchDedupeCache(is)(cache))(DebugLog.FetchType.FetchBatchDedupe(is))

  def batchLazy(iSet: Set[I]): LazyRequest[F, Map[I, A]] = LazyRequest(
    Kleisli { c =>
      LazyRequest.ReqInfo
        .fetch[F, Map[I, A]](
          prevCache = c,
          fetchId = wrappedId,
          reqId = iSet,
          isBatch = true,
          doSingle = (s, sCache) =>
            singleDedupeCache(s.asInstanceOf[I])(sCache)
              .asInstanceOf[F[DedupedRequest[F, Any]]],
          doBatch = (b, bCache) =>
            batchDedupeCache(b.asInstanceOf[Set[I]])(bCache)
              .asInstanceOf[F[DedupedRequest[F, Map[Any, Any]]]],
          getResultK = Kleisli[F, FetchCache, DedupedRequest[F, Map[I, A]]] { kCache =>
            val last = kCache
              .getMap(this)
              .collect {
                case (i, v) if (iSet.contains(i)) => i -> v
              }
            DedupedRequest[F, Map[I, A]](kCache, last).pure[F]
          },
          mapTo = _.asInstanceOf[DedupedRequest[F, Map[I, A]]]
        )
        .pure[F]
    }
  )

  /** Gets the current chain of debug logs */
  def getDebugLogs: F[Chain[DebugLog[I]]]

  /** Gets the current chain of debug logs and then resets the chain */
  def flushLogs: F[Chain[DebugLog[I]]]

  /** Resets the chain of logs without requesting them */
  def reset: F[Unit]
}

abstract class DebugAllFetch[F[_]: Concurrent: Clock, I, A](
    fetch: AllFetch[F, I, A],
    ref: Ref[F, Chain[DebugLog[I]]]
) extends DebugFetch[F, I, A](fetch, ref)
    with AllFetch[F, I, A]

object DebugFetch {
  def wrap[F[_]: Concurrent: Clock, I, A](fetch: Fetch[F, I, A]): F[DebugFetch[F, I, A]] =
    Concurrent[F].ref(Chain.empty[DebugLog[I]]).map { ref =>
      new DebugFetch[F, I, A](fetch, ref) {

        def getDebugLogs: F[Chain[DebugLog[I]]] = ref.get

        def flushLogs: F[Chain[DebugLog[I]]] = ref.modify(c => (Chain.empty, c))

        def reset: F[Unit] = ref.set(Chain.empty)

      }
    }
}
