package fetchless

import cats._
import cats.arrow.Profunctor
import cats.effect.Clock
import cats.data.Kleisli
import cats.syntax.all._
import fs2.Stream
import scala.concurrent.duration.FiniteDuration

/**
 * The ability to fetch values `A` given an ID `I`. Represents a data source such as a database,
 * cache, or other possibly remote resource.
 */
trait Fetch[F[_], I, A] {

  /** Unique string ID used for deduping fetches */
  val id: String

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

  /**
   * Returns a new `Fetch` instance that recovers from errors, derived from the current `Fetch`
   * instance. For example, if you are given an unsafe `Fetch` instance that will raise errors on
   * conditions you want to recover from gracefully, you can specify them using this wrapper.
   *
   * This takes two arguments, both `PartialFunction` from `Throwable` to the result type of
   * `single` and `batch` operations respectively.
   */
  def recoverWith(
      pfSingle: PartialFunction[Throwable, F[Option[A]]]
  )(
      pfBatch: PartialFunction[Throwable, F[Map[I, A]]]
  )(implicit F: MonadThrow[F]): Fetch[F, I, A] =
    Fetch.default[F, I, A](id)(
      single(_).recoverWith(pfSingle),
      batch(_).recoverWith(pfBatch)
    )
}

object Fetch {

  // Internally-used `Fetch` boilerplate instance for deriving behavior from the provided single/batch functions.
  private def default[F[_]: Applicative, I, A](
      fetchId: String
  )(fs: I => F[Option[A]], fb: Set[I] => F[Map[I, A]]) =
    new Fetch[F, I, A] {

      val id: String = fetchId

      def single(i: I): F[Option[A]] = fs(i)

      def singleDedupe(i: I): F[DedupedRequest[F, Option[A]]] =
        single(i).map { oa =>
          val cache = FetchCache(Map((i -> wrappedId) -> oa), Set.empty)
          DedupedRequest(cache, oa)
        }

      def singleDedupeCache(i: I)(cache: FetchCache): F[DedupedRequest[F, Option[A]]] =
        cache
          .get(this)(i) match {
          case Some(existing) =>
            DedupedRequest(cache, last = existing.some).pure[F]
          case None =>
            single(i).map {
              case Some(a) =>
                DedupedRequest(cache + ((i -> wrappedId) -> Some(a)), Some(a))
              case None => DedupedRequest(cache + ((i -> wrappedId) -> none), none)
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
                  .copy(last = kCache.get(this)(i))
                  .pure[F]
              },
              mapTo = _.asInstanceOf[DedupedRequest[F, Option[A]]]
            )
            .pure[F]
        }
      )

      def batch(iSet: Set[I]): F[Map[I, A]] = fb(iSet)

      def batchDedupe(iSet: Set[I]): F[DedupedRequest[F, Map[I, A]]] =
        batch(iSet).map { resultMap =>
          val missing = iSet.diff(resultMap.keySet)
          val initialCache: FetchCache = FetchCache(
            Cols.map(resultMap) { case (i, a) =>
              (i -> wrappedId) -> a.some
            },
            Set.empty
          )
          val missingCache: FetchCache =
            FetchCache(missing.toList.map(i => (i -> wrappedId) -> none[A]).toMap, Set.empty)
          DedupedRequest(initialCache ++ missingCache, resultMap)
        }

      def batchDedupeCache(iSet: Set[I])(cache: FetchCache): F[DedupedRequest[F, Map[I, A]]] = {
        val (needed, existing) = iSet.foldLeft(Set.empty[I], Map.empty[I, A]) {
          case ((iSet, cached), i) =>
            cache.get(this)(i) match {
              case Some(a) => (iSet, cached + (i -> a.asInstanceOf[A]))
              case None    => (iSet + i, cached)
            }
        }
        if (needed.isEmpty) {
          DedupedRequest(cache, cache.getMapForSet(this)(iSet)).pure[F]
        } else {
          batchDedupe(needed).map { result =>
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
  )(f: I => F[Option[A]]): Fetch[F, I, A] = default[F, I, A](fetchId)(
    f,
    iSet => iSet.toList.traverse(i => f(i).map(_.tupleLeft(i))).map(_.flattenOption.toMap)
  )

  /**
   * Allows creating a `Fetch` instance for some data source that does not allow for batching.
   * Batches are implemented as sequenced single fetches with no parallelism.
   */
  def singleParallel[F[_]: Monad: Parallel, I, A](
      fetchId: String
  )(f: I => F[Option[A]]): Fetch[F, I, A] = default[F, I, A](fetchId)(
    f,
    iSet => iSet.toList.parTraverse(i => f(i).map(_.tupleLeft(i))).map(_.flattenOption.toMap)
  )

  /** A `Fetch` instance that has separate single and batch fetch implementations. */
  def batchable[F[_]: Monad, I, A](fetchId: String)(single: I => F[Option[A]])(
      batch: Set[I] => F[Map[I, A]]
  ): Fetch[F, I, A] = default[F, I, A](fetchId)(single, batch)

  /**
   * A `Fetch` instance that has only the ability to make batch requests. Single fetches are
   * implemented in terms of batches. Useful for cases where there is only one method of fetching
   * data from your source and it allows for batching.
   */
  def batchOnly[F[_]: Monad, I, A](
      fetchId: String
  )(batchFunction: Set[I] => F[Map[I, A]]): Fetch[F, I, A] =
    default[F, I, A](fetchId)(i => batchFunction(Set(i)).map(_.get(i)), batchFunction)

  /** A `Fetch` instance backed by a local map. Useful for testing, debugging, or other usages. */
  def const[F[_]: Monad, I, A](fetchId: String)(map: Map[I, A]) = {
    default[F, I, A](fetchId)(
      i => map.get(i).pure[F],
      iSet => map.filter { case (i, _) => iSet.contains(i) }.pure[F]
    )
  }

  /**
   * A `Fetch` instance that always returns the same value that you give it.
   */
  def echo[F[_]: Monad, I](fetchId: String) = default[F, I, I](fetchId)(
    i => i.some.pure[F],
    iSet => iSet.toList.map(i => i -> i).toMap.pure[F]
  )

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
        default[F, C, D](fab.id)(
          i => fab.single(f(i)).map(_.map(g)),
          { is =>
            val setMap = is.toList.map(c => f(c) -> c).toMap
            val inSet  = setMap.keySet
            fab.batch(inSet).map(Cols.map(_) { case (a, b) => setMap.apply(a) -> g(b) })
          }
        )

      // Override for efficiency, don't think we'd gain much overriding the lmap case
      // since that case still has to do the set/map behavior defined above
      override def rmap[A, B, C](fab: Fetch[F, A, B])(f: B => C): Fetch[F, A, C] =
        default[F, A, C](fab.id)(
          i => fab.single(i).map(_.map(f)),
          is => fab.batch(is).map(Cols.mapValues(_)(f))
        )
    }
}
