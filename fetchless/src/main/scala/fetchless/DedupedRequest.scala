package fetchless

import cats.Applicative
import cats.syntax.all._
import cats.FlatMap
import cats.Monad
import cats.Functor

/**
 * A fetch request after being ran, along with a local cache of other recent fetches done in the
 * current sequence, for deduping.
 *
 * To access values from the cache, use the `access` method rather than getting values directly as
 * it is unsafe and not suitable for regular use.
 *
 * @param unsafeCache
 *   A cache of values used for linear deduping. Do not access directly, but use `access` instead.
 * @param last
 *   The last value retrieved in sequence.
 */
final case class DedupedRequest[F[_], A](
    unsafeCache: FetchCache,
    last: A
) {

  /** Fetch another value using this cache as a base. */
  def alsoFetch[I, B](
      i: I
  )(implicit fetch: Fetch[F, I, B]): F[DedupedRequest[F, Option[B]]] =
    fetch.singleDedupeCache(i)(unsafeCache)

  /** Fetch another value using this cache as a base. */
  def alsoFetch[I, B](fetch: Fetch[F, I, B])(i: I): F[DedupedRequest[F, Option[B]]] =
    fetch.singleDedupeCache(i)(unsafeCache)

  /** Fetch a set of values using this cache as a base. */
  def alsoFetchAll[I, B](is: Set[I])(implicit
      fetch: Fetch[F, I, B]
  ): F[DedupedRequest[F, Map[I, B]]] =
    fetch.batchDedupeCache(is)(unsafeCache)

  /** Fetch a set of values using this cache as a base. */
  def alsoFetchAll[I, B](fetch: Fetch[F, I, B])(is: Set[I]): F[DedupedRequest[F, Map[I, B]]] =
    fetch.batchDedupeCache(is)(unsafeCache)

  /**
   * Fold another deduped fetch request into this one, so that the cache will contain values from
   * both fetches.
   */
  def absorb[B](df: DedupedRequest[F, B]): DedupedRequest[F, B] =
    df.copy(
      unsafeCache = unsafeCache ++ df.unsafeCache
    )

  /**
   * Filter the deduped cache results by the selected `Fetch` instance. If any results exist for the
   * instance supplied, they will be presented in a new map.
   */
  def access[I, B](implicit fetch: Fetch[F, I, B]) = unsafeCache.getMap(fetch)
}

object DedupedRequest {

  def prepopulated[F[_]](cache: FetchCache): DedupedRequest[F, Unit] =
    DedupedRequest(cache, ())

  def empty[F[_]]: DedupedRequest[F, Unit] = prepopulated(FetchCache.empty)

  /** `cats.Monad` instance for `DedupedRequest` */
  implicit def dedupedRequestM[F[_]: Monad]: Monad[DedupedRequest[F, *]] =
    new Monad[DedupedRequest[F, *]] {
      def flatMap[A, B](fa: DedupedRequest[F, A])(
          f: A => DedupedRequest[F, B]
      ): DedupedRequest[F, B] =
        fa.absorb(f(fa.last))

      def tailRecM[A, B](a: A)(f: A => DedupedRequest[F, Either[A, B]]): DedupedRequest[F, B] = {
        val fab = f(a)
        fab.last match {
          case Left(value)  => tailRecM(value)(f)
          case Right(value) => fab.copy(last = value)
        }
      }

      def pure[A](x: A): DedupedRequest[F, A] = DedupedRequest(FetchCache.empty, x)

    }

  implicit def dedupedRequestA[F[_]: Applicative]: Applicative[DedupedRequest[F, *]] =
    new Applicative[DedupedRequest[F, *]] {
      def ap[A, B](ff: DedupedRequest[F, A => B])(fa: DedupedRequest[F, A]): DedupedRequest[F, B] =
        ff.absorb(fa.copy(last = ff.last(fa.last)))

      def pure[A](x: A): DedupedRequest[F, A] = DedupedRequest[F, A](FetchCache.empty, x)

    }

  implicit def dedupedRequestF[F[_]: Functor]: Functor[DedupedRequest[F, *]] =
    new Functor[DedupedRequest[F, *]] {
      def map[A, B](fa: DedupedRequest[F, A])(f: A => B): DedupedRequest[F, B] =
        fa.copy(last = f(fa.last))
    }
}
