package fetchless

import cats.syntax.all._
import cats.{Applicative, Functor, Traverse}
import cats.effect.Clock
import scala.concurrent.duration.FiniteDuration
import cats.Parallel
import cats.Monad
import cats.data.Kleisli

/**
 * The ability to fetch values `A` given an ID `I`. Represents a data source such as a database,
 * cache, or other possibly remote resource.
 */
trait Fetch[F[_], I, A] {

  /** Unique string ID used for deduping fetches */
  val id: String

  /** Immediately requests a single value */
  def single(i: I): F[Option[A]]

  /**
   * Immediately requests a single value alongside a local cache for future fetches to dedupe with.
   */
  def singleDedupe(i: I)(implicit F: Functor[F]): F[DedupedFetch[F, Option[A]]] =
    F.map(single(i))(oa => DedupedFetch(Map((i -> id) -> oa), oa))

  /** Same as `singleDedupe` only you pre-supply the cache. */
  def singleDedupeCache(
      i: I
  )(cache: CacheMap)(implicit F: Monad[F]): F[DedupedFetch[F, Option[A]]] =
    cache
      .get(i -> id)
      .pure[F]
      .flatMap {
        case Some(existing) => DedupedFetch(cache, last = existing.asInstanceOf[Option[A]]).pure[F]
        case None =>
          single(i).map {
            case Some(a) => DedupedFetch(cache + ((i -> id) -> a.some), a.some)
            case None    => DedupedFetch(cache + ((i -> id) -> none), none)
          }
      }

  /**
   * A version of `singleDedupe` returning a `LazyFetch` instead, which has not been run yet and can
   * be chained into other requests.
   */
  def singleLazy(i: I)(implicit F: Monad[F]): LazyFetch[F, Option[A]] = LazyFetch(
    Kleisli(c => singleDedupeCache(i)(c))
  )

  /** Immediately requests a batch of values. */
  def batch(iSet: Set[I]): F[Map[I, A]]

  /** Immediately requests a batch of values. */
  def batch[G[_]: Traverse](is: G[I]): F[Map[I, A]] =
    batch(is.toIterable.toSet)

  /**
   * Immediately requests a batch of values alongside a local cache for future fetches to dedupe
   * with.
   */
  def batchDedupe(iSet: Set[I])(implicit F: Functor[F]): F[DedupedFetch[F, Map[I, A]]] =
    F.map(batch(iSet)) { resultMap =>
      val missing = iSet.diff(resultMap.keySet)
      val initialCache: CacheMap =
        resultMap.view.map[(I, String), Option[A]] { case (i, a) => (i -> id) -> a.some }.toMap
      val missingCache: CacheMap = missing.toList.map(i => (i -> id) -> none[A]).toMap
      DedupedFetch(initialCache ++ missingCache, resultMap)
    }

  /**
   * Immediately requests a batch of values alongside a local cache for future fetches to dedupe
   * with.
   */
  def batchDedupe[G[_]: Traverse](is: G[I])(implicit F: Functor[F]): F[DedupedFetch[F, Map[I, A]]] =
    batchDedupe(is.toIterable.toSet)

  /** Same as `batchDedupe` only you pre-supply the cache. */
  def batchDedupeCache(
      is: Set[I]
  )(cache: CacheMap)(implicit F: Monad[F]): F[DedupedFetch[F, Map[I, A]]] = {
    val (needed, existing) = is.foldLeft(Set.empty[I], Map.empty[I, A]) {
      case ((iSet, cached), i) =>
        cache.get(i, id) match {
          case Some(a) => (iSet, cached + (i -> a.asInstanceOf[A]))
          case None    => (iSet + i, cached)
        }
    }
    batch(needed).map { resultMap =>
      val newlyCacheable = resultMap.view.map { case (i, a) => (i -> id) -> a.some }.toMap
      val missing        = needed.diff(resultMap.keySet).toList.map(i => (i -> id) -> none)
      DedupedFetch(cache ++ newlyCacheable ++ missing, resultMap)
    }
  }

  /** Same as `batchDedupe` only you pre-supply the cache. */
  def batchDedupeCache[G[_]: Traverse](is: G[I])(cache: CacheMap)(implicit
      F: Monad[F]
  ): F[DedupedFetch[F, Map[I, A]]] =
    batchDedupe(is.toIterable.toSet)

  /**
   * A version of `batchDedupe` returning a `LazyFetch` instead, which has not been run yet and can
   * be chained into other requests.
   */
  def batchLazy(iSet: Set[I])(implicit F: Monad[F]): LazyFetch[F, Map[I, A]] = LazyFetch(
    Kleisli(c => batchDedupeCache(iSet)(c))
  )

  /**
   * A version of `batchDedupe` returning a `LazyFetch` instead, which has not been run yet and can
   * be chained into other requests.
   */
  def batchLazy[G[_]: Traverse](is: G[I])(implicit F: Monad[F]): LazyFetch[F, Map[I, A]] =
    batchLazy(is.toIterable.toSet)
}

object Fetch {

  /**
   * Allows creating a `Fetch` instance for some data source that does not allow for batching.
   * Batches are implemented as sequenced single fetches with no parallelism.
   */
  def singleSequenced[F[_]: Applicative, I, A](
      fetchId: String
  )(f: I => F[Option[A]]): Fetch[F, I, A] =
    new Fetch[F, I, A] {
      val id           = fetchId
      def single(i: I) = f(i)
      def batch(iSet: Set[I]) = iSet.toList
        .traverse { i =>
          f(i).map(_.tupleLeft(i))
        }
        .map(_.flattenOption.toMap)
    }

  /**
   * Allows creating a `Fetch` instance for some data source that does not allow for batching.
   * Batches are implemented as sequenced single fetches with no parallelism.
   */
  def singleParallel[F[_]: Applicative: Parallel, I, A](fetchId: String)(
      f: I => F[Option[A]]
  ): Fetch[F, I, A] =
    new Fetch[F, I, A] {
      val id           = fetchId
      def single(i: I) = f(i)
      def batch(iSet: Set[I]) = iSet.toList
        .parTraverse { i =>
          f(i).map(_.tupleLeft(i))
        }
        .map(_.flattenOption.toMap)
    }

  /** A `Fetch` instance that has separate single and batch fetch implementations. */
  def batchable[F[_], I, A](fetchId: String)(
      singleFunction: I => F[Option[A]]
  )(batchFunction: Set[I] => F[Map[I, A]]): Fetch[F, I, A] = new Fetch[F, I, A] {
    val id                  = fetchId
    def single(i: I)        = singleFunction(i)
    def batch(iSet: Set[I]) = batchFunction(iSet)
  }

  /**
   * A `Fetch` instanced that has only the ability to make batch requests. Single fetches are
   * implemented in terms of batches. Useful for cases where there is only one method of fetching
   * data from your source and it allows for batching.
   */
  def batchOnly[F[_]: Functor, I, A](
      fetchId: String
  )(batchFunction: Set[I] => F[Map[I, A]]): Fetch[F, I, A] =
    new Fetch[F, I, A] {
      val id                                = fetchId
      def single(i: I): F[Option[A]]        = batch(Set(i)).map(_.get(i))
      def batch(iSet: Set[I]): F[Map[I, A]] = batchFunction(iSet)
    }

  /** A `Fetch` instance backed by a local map. Useful for testing, debugging, or other usages. */
  def const[F[_]: Applicative, I, A](fetchId: String)(map: Map[I, A]) = new Fetch[F, I, A] {
    private val dedupedMap: CacheMap = map.toList.map { case (i, a) => (i -> id) -> a.some }.toMap
    val id                           = fetchId
    def single(i: I): F[Option[A]]   = map.get(i).pure[F]
    def batch(iSet: Set[I]): F[Map[I, A]] = map.filter { case (i, _) => iSet.contains(i) }.pure[F]

    // TODO: more overrides
    override def singleDedupe(i: I)(implicit F: Functor[F]): F[DedupedFetch[F, Option[A]]] =
      DedupedFetch(dedupedMap, map.get(i)).pure[F]
    override def batchDedupe(iSet: Set[I])(implicit F: Functor[F]): F[DedupedFetch[F, Map[I, A]]] =
      DedupedFetch(dedupedMap, map.filter { case (i, _) => iSet.contains(i) }).pure[F]
  }

  /**
   * A `Fetch` instance that always returns the same value that you give it.
   *
   * NOTE: All returned dedupe caches are empty, except when otherwise passed in manually, as this
   * will always return an element.
   */
  def echo[F[_]: Applicative, I](fetchId: String) = new Fetch[F, I, I] {
    val id: String = fetchId

    def single(i: I): F[Option[I]] = i.some.pure[F]

    def batch(iSet: Set[I]): F[Map[I, I]] = iSet.toList.map(i => i -> i).toMap.pure[F]

    // TODO: more overrides
    override def singleDedupe(i: I)(implicit F: Functor[F]): F[DedupedFetch[F, Option[I]]] =
      DedupedFetch(Map.empty, i.some).pure[F]

    override def singleDedupeCache(i: I)(cache: CacheMap)(implicit
        F: Monad[F]
    ): F[DedupedFetch[F, Option[I]]] =
      singleDedupe(i).map(_.copy(cache = cache))

    override def batchDedupe(iSet: Set[I])(implicit F: Functor[F]): F[DedupedFetch[F, Map[I, I]]] =
      DedupedFetch(Map.empty, iSet.toList.map(i => i -> i).toMap).pure[F]

    override def batchDedupeCache(iSet: Set[I])(cache: CacheMap)(implicit
        F: Monad[F]
    ): F[DedupedFetch[F, Map[I, I]]] =
      batchDedupe(iSet).map(_.copy(cache = cache))
  }
}
