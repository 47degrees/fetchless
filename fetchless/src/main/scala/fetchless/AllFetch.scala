package fetchless

import cats.{Applicative, Monad}
import cats.syntax.all._
import cats.data.Kleisli

/**
 * A variant of `Fetch` that allows you to request all available elements at once, without providing
 * IDs.
 *
 * Each of the `batchAll` and related methods will override whatever is currently in your cache on
 * their first sequenced run. All future requests in the current deduplicated sequence will always
 * try to retrieve a value from the cache rather than making a request.
 */
trait AllFetch[F[_], I, A] extends Fetch[F, I, A] {

  /** Get every single possible element from this `Fetch` at once. */
  def batchAll: F[Map[I, A]]

  /**
   * Get every possible element from this `Fetch` but as a `DedupedRequest`, allowing you to dedupe
   * future requests after this one. All requests using the provided cache will see that all
   * possible values have been cached, and no requests will be made for values related to this
   * `Fetch` instance.
   */
  def batchAllDedupe: F[DedupedRequest[F, Map[I, A]]]

  /**
   * Same as `batchAllDedupe` but using an initial cache. All requests using the provided cache will
   * see that all possible values have been cached, and no requests will be made for values related
   * to this `Fetch` instance.
   */
  def batchAllDedupeCache(cache: FetchCache)(implicit
      F: Applicative[F]
  ): F[DedupedRequest[F, Map[I, A]]]

  /**
   * A lazy request for all elements from this `Fetch`. Like `batchAllDedupe` it will ignore the
   * existing cache for the request but it will keep the current state of the cache when chaining
   * requests.
   */
  def batchAllLazy(implicit F: Applicative[F]): LazyRequest[F, Map[I, A]]
}

object AllFetch {

  /**
   * Turns any existing `Fetch` into an `AllFetch` by supplying a method to batch all known values
   * at once.
   *
   * Any calls to `batchAll` and related methods will override whatever is in the deduplication
   * cache on first call. This means that there may be some inconsistencies as deduplication is
   * ignored the first time all values are requested. Afterward, future requests to this `Fetch`
   * instance will always and only check the cache.
   */
  def fromExisting[F[_]: Monad, I, A](fetch: Fetch[F, I, A])(doBatchAll: F[Map[I, A]]) =
    new AllFetch[F, I, A] {
      val id: String = fetch.id

      def single(i: I): F[Option[A]] = fetch.single(i)

      def singleDedupe(i: I): F[DedupedRequest[F, Option[A]]] = fetch.singleDedupe(i)

      def singleDedupeCache(i: I)(cache: FetchCache): F[DedupedRequest[F, Option[A]]] =
        fetch.singleDedupeCache(i)(cache)

      def batch(iSet: Set[I]): F[Map[I, A]] = fetch.batch(iSet)

      def batchDedupe(iSet: Set[I]): F[DedupedRequest[F, Map[I, A]]] = fetch.batchDedupe(iSet)

      def batchDedupeCache(iSet: Set[I])(cache: FetchCache): F[DedupedRequest[F, Map[I, A]]] =
        fetch.batchDedupeCache(iSet)(cache)

      def batchAll: F[Map[I, A]] = doBatchAll

      def batchAllDedupe: F[DedupedRequest[F, Map[I, A]]] = batchAll.map { m =>
        val fetchCache =
          FetchCache(
            m.toList.map { case (i, a) => (i -> fetch.wrappedId) -> a.some }.toMap,
            Set(fetch.wrappedId)
          )
        DedupedRequest(fetchCache, m)
      }

      def batchAllDedupeCache(
          cache: FetchCache
      )(implicit F: Applicative[F]): F[DedupedRequest[F, Map[I, A]]] =
        if (cache.fetchAllAcc.contains(fetch.wrappedId)) {
          DedupedRequest[F, Map[I, A]](cache, cache.getMap(fetch)).pure[F]
        } else {
          batchAllDedupe.map { df =>
            df.copy(unsafeCache = cache ++ df.unsafeCache)
          }
        }

      def batchAllLazy(implicit F: Applicative[F]): LazyRequest[F, Map[I, A]] = LazyRequest(
        Kleisli { c =>
          LazyRequest.ReqInfo
            .fetchAll(
              c,
              wrappedId,
              Kleisli(batchAllDedupeCache(_).asInstanceOf[F[DedupedRequest[F, Any]]]),
              Kleisli[F, FetchCache, DedupedRequest[F, Map[I, A]]] { fetchCache =>
                DedupedRequest(
                  fetchCache,
                  fetchCache.getMap(this)
                )
                  .pure[F]
              }
            )
            .pure[F]
        }
      )

    }
}
