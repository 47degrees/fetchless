package fetchless

import fetchless.CacheMap
import cats.Monad
import cats.syntax.all._
import cats.data.Kleisli

/**
 * A variant of `Fetch` that allows you to request all available elements at once, without providing
 * IDs.
 *
 * Each of the `batchAll` and related methods overrides the deduplication functionality of normal
 * `Fetch` usage, so they are mainly useful for scenarios where you want to keep the `Fetch`
 * interface for your data source while retaining the option to get everything at once, as
 * necessary.
 */
trait AllFetch[F[_], I, A] extends Fetch[F, I, A] {

  /** Get every single possible element from this `Fetch` at once. */
  def batchAll: F[Map[I, A]]

  /**
   * Get every possible element from this `Fetch` but as a `DedupedRequest`, allowing you to dedupe
   * future requests after this one.
   */
  def batchAllDedupe: F[DedupedRequest[F, Map[I, A]]]

  /**
   * Same as `batchAllDedupe` but using an initial cache. Deduping from this cache is ignored for
   * this step as all elements are requested explicitly, but any other unrelated cached results are
   * kept.
   */
  def batchAllDedupeCache(cache: CacheMap): F[DedupedRequest[F, Map[I, A]]]

  /**
   * A lazy request for all elements from this `Fetch`. Like `batchAllDedupe` it will ignore the
   * existing cache for the request but it will keep the current state of the cache when chaining
   * requests.
   */
  def batchAllLazy: LazyRequest[F, Map[I, A]]
}

object AllFetch {

  /**
   * Turns any existing `Fetch` into an `AllFetch` by supplying a method to batch all known values
   * at once.
   *
   * Any calls to `batchAll` and related methods will override whatever is in the deduplication
   * cache and you will always request all elements. This means that there may be some
   * inconsistencies as deduplication is ignored. For example, you can use them as a way to
   * prepopulate your result cache before making future requests.
   */
  def fromExisting[F[_]: Monad, I, A](fetch: Fetch[F, I, A])(doBatchAll: F[Map[I, A]]) =
    new AllFetch[F, I, A] {
      val id: String = fetch.id

      def single(i: I): F[Option[A]] = fetch.single(i)

      def singleDedupe(i: I): F[DedupedRequest[F, Option[A]]] = fetch.singleDedupe(i)

      def singleDedupeCache(i: I)(cache: CacheMap): F[DedupedRequest[F, Option[A]]] =
        fetch.singleDedupeCache(i)(cache)

      def singleLazyWrap[B](i: I)(
          f: F[DedupedRequest[F, Option[A]]] => F[DedupedRequest[F, B]]
      ): LazyRequest[F, B] = fetch.singleLazyWrap(i)(f)

      def batch(iSet: Set[I]): F[Map[I, A]] = fetch.batch(iSet)

      def batchDedupe(iSet: Set[I]): F[DedupedRequest[F, Map[I, A]]] = fetch.batchDedupe(iSet)

      def batchDedupeCache(iSet: Set[I])(cache: CacheMap): F[DedupedRequest[F, Map[I, A]]] =
        fetch.batchDedupeCache(iSet)(cache)

      def batchLazyWrap[B](iSet: Set[I])(
          f: F[DedupedRequest[F, Map[I, A]]] => F[DedupedRequest[F, B]]
      ): LazyRequest[F, B] = fetch.batchLazyWrap(iSet)(f)

      def batchAll: F[Map[I, A]] = doBatchAll

      def batchAllDedupe: F[DedupedRequest[F, Map[I, A]]] = batchAll.map { m =>
        val cacheMap =
          m.toList.map { case (i, a) => (i -> fetch.id) -> a.some }.toMap.asInstanceOf[CacheMap]
        DedupedRequest(cacheMap, m)
      }

      def batchAllDedupeCache(cache: CacheMap): F[DedupedRequest[F, Map[I, A]]] = batchAll.map {
        m =>
          val cacheMap =
            cache ++ m.toList
              .map { case (i, a) => (i -> fetch.id) -> a.some }
              .toMap
              .asInstanceOf[CacheMap]
          DedupedRequest(cacheMap, m)
      }

      def batchAllLazy: LazyRequest[F, Map[I, A]] = LazyRequest(Kleisli { c =>
        batchAllDedupeCache(c)
      })

    }
}
