package fetchless

import cats._
import cats.arrow.Profunctor
import cats.effect.Clock
import cats.data.Kleisli
import cats.syntax.all._
import fs2.Stream
import scala.concurrent.duration.FiniteDuration
import cats.data.Chain

/**
 * The ability to fetch values `A` given an ID `I`. Represents a data source such as a database,
 * cache, or other possibly remote resource.
 */
trait Fetch[F[_], I, A] {

  /** Unique string ID used for deduping fetches */
  val id: String

  val timer: FetchTimer[F]

  /** The ID of this fetch, in a typed wrapper used for internal comparisons. */
  lazy val wrappedId: FetchId.StringId = FetchId.StringId(id)

  /** Immediately requests a single value */
  def single(i: I): F[Option[A]]

  /**
   * Immediately requests a single value alongside a local cache for future fetches to dedupe with.
   */
  def singleDedupe(i: I): F[DedupedRequest[F, Option[A]]]

  /** Same as `singleDedupe` only you pre-supply the cache. */
  def singleDedupeCache(i: I)(cache: FetchCache): F[DedupedRequest[F, Option[A]]]

  /**
   * A version of `singleDedupe` returning a `LazyRequest` instead, which has not been run yet and
   * can be chained into other requests.
   */
  def singleLazy(i: I): LazyRequest[F, Option[A]]

  /** Immediately requests a batch of values. */
  def batch(iSet: Set[I]): F[Map[I, A]]

  /** Immediately requests a batch of values. */
  def batch[G[_]: Traverse](is: G[I]): F[Map[I, A]] =
    batch(is.toIterable.toSet)

  /**
   * Immediately requests a batch of values alongside a local cache for future fetches to dedupe
   * with.
   */
  def batchDedupe(iSet: Set[I]): F[DedupedRequest[F, Map[I, A]]]

  /**
   * Immediately requests a batch of values alongside a local cache for future fetches to dedupe
   * with.
   */
  def batchDedupe[G[_]: Traverse](is: G[I]): F[DedupedRequest[F, Map[I, A]]] =
    batchDedupe(is.toIterable.toSet)

  /** Same as `batchDedupe` only you pre-supply the cache. */
  def batchDedupeCache(iSet: Set[I])(cache: FetchCache): F[DedupedRequest[F, Map[I, A]]]

  /** Same as `batchDedupe` only you pre-supply the cache. */
  def batchDedupeCache[G[_]: Traverse](iSet: G[I])(
      cache: FetchCache
  ): F[DedupedRequest[F, Map[I, A]]] =
    batchDedupe(iSet.toIterable.toSet)

  /**
   * A version of `batchDedupe` returning a `LazyRequest` instead, which has not been run yet and
   * can be chained into other requests.
   */
  def batchLazy(iSet: Set[I]): LazyRequest[F, Map[I, A]]

  /**
   * A version of `batchDedupe` returning a `LazyRequest` instead, which has not been run yet and
   * can be chained into other requests.
   */
  def batchLazy[G[_]: Traverse](is: G[I])(implicit F: Applicative[F]): LazyRequest[F, Map[I, A]] =
    batchLazy(is.toIterable.toSet)
}

object Fetch {

  // Internally-used `Fetch` boilerplate instance for deriving behavior from the provided single/batch functions.
  private def default[F[_]: Applicative, I, A](
      fetchId: String,
      fetchTimer: FetchTimer[F]
  )(fs: I => F[Option[A]], fb: Set[I] => F[Map[I, A]]) =
    new Fetch[F, I, A] {

      val id = fetchId

      val timer = fetchTimer

      def single(i: I): F[Option[A]] = fs(i)

      def singleDedupe(i: I): F[DedupedRequest[F, Option[A]]] =
        timer.time(single(i)).map { case (time, oa) =>
          val logEntry = if (oa.isDefined) {
            FetchCache.RequestLogEntry
              .SingleRequest(
                wrappedId,
                i,
                time,
                FetchCache.SingleRequestResult.ValueFound
              )
          } else {
            FetchCache.RequestLogEntry
              .SingleRequest(
                wrappedId,
                i,
                time,
                FetchCache.SingleRequestResult.ValueNotFound
              )
          }
          val cache = FetchCache(Map((i -> wrappedId) -> oa), Set.empty, Chain.one(logEntry))
          DedupedRequest(cache, oa)
        }

      def singleDedupeCache(i: I)(cache: FetchCache): F[DedupedRequest[F, Option[A]]] =
        cache
          .get(this)(i) match {
          case FetchCache.GetValueResult.ValueExists(existing) =>
            val newLog = FetchCache.RequestLogEntry.SingleRequest(
              wrappedId,
              i,
              FetchCache.ResultTime.Instantaneous,
              FetchCache.SingleRequestResult.ValueFound
            )
            DedupedRequest(cache.addLog(newLog), last = existing.some).pure[F]
          case FetchCache.GetValueResult.ValueDoesNotExist() =>
            val newLog = FetchCache.RequestLogEntry.SingleRequest(
              wrappedId,
              i,
              FetchCache.ResultTime.Instantaneous,
              FetchCache.SingleRequestResult.ValueNotFound
            )
            DedupedRequest(cache.addLog(newLog), none[A]).pure[F]
          case FetchCache.GetValueResult.ValueNotYetRequested() =>
            singleDedupe(i).map {
              DedupedRequest.prepopulated[F](cache).absorb(_)
            }
        }

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
                  .copy(
                    last = kCache.get(this)(i) match {
                      case FetchCache.GetValueResult.ValueExists(v) => v.some
                      case _                                        => none
                    }
                  )
                  .pure[F]
              },
              mapTo = _.asInstanceOf[DedupedRequest[F, Option[A]]]
            )
            .pure[F]
        }
      )

      def batch(iSet: Set[I]): F[Map[I, A]] = fb(iSet)

      def batchDedupe(iSet: Set[I]): F[DedupedRequest[F, Map[I, A]]] = batchDedupeContext(iSet)

      // Internal helper to provide context from the cache
      private def batchDedupeContext(
          iSet: Set[I],
          existingIds: Set[I] = Set.empty,
          missingIds: Set[I] = Set.empty
      ): F[DedupedRequest[F, Map[I, A]]] =
        timer.time(batch(iSet)).map { case (t, resultMap) =>
          val missing = iSet.diff(resultMap.keySet)
          val initialCache: FetchCache = FetchCache(
            resultMap.view
              .map[(I, FetchId), Option[A]] { case (i, a) => (i -> wrappedId) -> a.some }
              .toMap,
            Set.empty,
            Chain.one(
              FetchCache.RequestLogEntry.BatchRequest(
                wrappedId,
                resultMap.keySet.asInstanceOf[Set[Any]] ++ existingIds.asInstanceOf[Set[Any]],
                missing.asInstanceOf[Set[Any]] ++ missingIds.asInstanceOf[Set[Any]],
                t
              )
            )
          )
          val missingCache: FetchCache =
            FetchCache(
              missing.toList.map(i => (i -> wrappedId) -> none[A]).toMap,
              Set.empty,
              Chain.empty
            )
          DedupedRequest(initialCache ++ missingCache, resultMap)
        }

      def batchDedupeCache(iSet: Set[I])(cache: FetchCache): F[DedupedRequest[F, Map[I, A]]] = {
        val (needed, missing, existing) =
          iSet.foldLeft(Set.empty[I], Set.empty[I], Map.empty[I, A]) { case ((n, m, e), i) =>
            cache.get(this)(i) match {
              case FetchCache.GetValueResult.ValueNotYetRequested() => (n + i, m, e)
              case FetchCache.GetValueResult.ValueExists(a) =>
                (n, m, e + (i -> a.asInstanceOf[A]))
              case _ => (n, m + i, e)
            }
          }
        if (needed.isEmpty) {
          DedupedRequest(
            cache.addLog(
              FetchCache.RequestLogEntry.BatchRequest(
                wrappedId,
                iSet.asInstanceOf[Set[Any]],
                Set.empty,
                FetchCache.ResultTime.Instantaneous
              )
            ),
            cache.getMapForSetOnlyExisting(this)(iSet)
          ).pure[F]
        } else {
          batchDedupeContext(needed, existing.keySet, missing).map { result =>
            DedupedRequest.prepopulated[F](cache).absorb(result)
          }
        }
      }

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
    }

  /**
   * Allows creating a `Fetch` instance for some data source that does not allow for batching.
   * Batches are implemented as sequenced single fetches with no parallelism.
   */
  def singleSequenced[F[_]: Monad, I, A](
      fetchId: String
  )(f: I => F[Option[A]]): Fetch[F, I, A] = default[F, I, A](fetchId, FetchTimer.noop)(
    f,
    iSet => iSet.toList.traverse(i => f(i).map(_.tupleLeft(i))).map(_.flattenOption.toMap)
  )

  /**
   * Allows creating a `Fetch` instance for some data source that does not allow for batching.
   * Batches are implemented as sequenced single fetches with no parallelism.
   */
  def singleParallel[F[_]: Monad: Parallel, I, A](
      fetchId: String
  )(f: I => F[Option[A]]): Fetch[F, I, A] = default[F, I, A](fetchId, FetchTimer.noop)(
    f,
    iSet => iSet.toList.parTraverse(i => f(i).map(_.tupleLeft(i))).map(_.flattenOption.toMap)
  )

  /** A `Fetch` instance that has separate single and batch fetch implementations. */
  def batchable[F[_]: Monad, I, A](fetchId: String)(single: I => F[Option[A]])(
      batch: Set[I] => F[Map[I, A]]
  ): Fetch[F, I, A] = default[F, I, A](fetchId, FetchTimer.noop)(single, batch)

  /**
   * A `Fetch` instance that has only the ability to make batch requests. Single fetches are
   * implemented in terms of batches. Useful for cases where there is only one method of fetching
   * data from your source and it allows for batching.
   */
  def batchOnly[F[_]: Monad, I, A](
      fetchId: String
  )(batchFunction: Set[I] => F[Map[I, A]]): Fetch[F, I, A] =
    default[F, I, A](fetchId, FetchTimer.noop)(
      i => batchFunction(Set(i)).map(_.get(i)),
      batchFunction
    )

  /** A `Fetch` instance backed by a local map. Useful for testing, debugging, or other usages. */
  def const[F[_]: Monad, I, A](fetchId: String)(map: Map[I, A]) = {
    default[F, I, A](fetchId, FetchTimer.noop)(
      i => map.get(i).pure[F],
      iSet => map.filter { case (i, _) => iSet.contains(i) }.pure[F]
    )
  }

  /**
   * A `Fetch` instance that always returns the same value that you give it.
   */
  def echo[F[_]: Monad, I](fetchId: String) = default[F, I, I](fetchId, FetchTimer.noop)(
    i => i.some.pure[F],
    iSet => iSet.toList.map(i => i -> i).toMap.pure[F]
  )

  /**
   * Returns a modified `Fetch` instance that recovers from errors, derived from the supplied
   * `Fetch` instance. For example, if you are given an unsafe `Fetch` instance that will raise
   * errors on conditions you want to recover from gracefully, you can handle those errors by
   * wrapping it with this.
   *
   * In addition to the fetch instance you are modifying, this takes two function arguments, both
   * `PartialFunction` from `Throwable` to the result type of `single` and `batch` operations
   * respectively.
   */
  def recoverWith[F[_], I, A](baseFetch: Fetch[F, I, A])(
      pfSingle: PartialFunction[Throwable, F[Option[A]]]
  )(
      pfBatch: PartialFunction[Throwable, F[Map[I, A]]]
  )(implicit F: MonadThrow[F]): Fetch[F, I, A] =
    Fetch.default[F, I, A](baseFetch.id, baseFetch.timer)(
      baseFetch.single(_).recoverWith(pfSingle),
      baseFetch.batch(_).recoverWith(pfBatch)
    )

  /**
   * Creates a `Fetch` with a built-in timer for logging access times for fetch requests. By
   * default, no times are recorded, so opting into this can give you more insight into how long
   * your requests are taking to complete.
   */
  def withTimer[F[_]: Applicative: Clock, I, A](
      baseFetch: Fetch[F, I, A]
  ): Fetch[F, I, A] =
    Fetch.default[F, I, A](baseFetch.id, FetchTimer.clock[F])(baseFetch.single _, baseFetch.batch _)

  /**
   * A `Profunctor` instance for `Fetch` so as to allow for calling `lmap`, `rmap`, and `dimap`
   * syntax on a valid `Fetch` instance. This will let you change the ID type (`lmap`) and the
   * output value type (`rmap`) without having to manually do the wrapping yourself.
   *
   * Note that efficiency when using `lmap` or `dimap` might be an issue since those cases need to
   * determine which input IDs match to which output IDs, so extra operations and allocations are
   * necessary. It might be faster in some cases to just create a new, optimized `Fetch` instance if
   * you are planning on mapping the input type.
   */
  implicit def fetchProfunctor[F[_]: Monad]: Profunctor[Fetch[F, *, *]] =
    new Profunctor[Fetch[F, *, *]] {
      def dimap[A, B, C, D](fab: Fetch[F, A, B])(f: C => A)(g: B => D): Fetch[F, C, D] =
        default[F, C, D](fab.id, FetchTimer.noop)(
          i => fab.single(f(i)).map(_.map(g)),
          { is =>
            val setMap = is.toList.map(c => f(c) -> c).toMap
            val inSet  = setMap.keySet
            fab.batch(inSet).map(m => m.map[C, D] { case (a, b) => setMap.apply(a) -> g(b) })
          }
        )

      // Override for efficiency, don't think we'd gain much overriding the lmap case
      // since that case still has to do the set/map behavior defined above
      override def rmap[A, B, C](fab: Fetch[F, A, B])(f: B => C): Fetch[F, A, C] =
        default[F, A, C](fab.id, FetchTimer.noop)(
          i => fab.single(i).map(_.map(f)),
          is => fab.batch(is).map(_.view.mapValues(f).toMap)
        )
    }
}
