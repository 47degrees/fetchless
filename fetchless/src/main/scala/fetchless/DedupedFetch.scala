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
final case class DedupedFetch[F[_], A](unsafeCache: CacheMap, last: A) {

  /** Fetch another value using this cache as a base. */
  def alsoFetch[I, B](
      i: I
  )(implicit fetch: Fetch[F, I, B]): F[DedupedFetch[F, Option[B]]] =
    fetch.singleDedupeCache(i)(unsafeCache)

  /** Fetch another value using this cache as a base. */
  def alsoFetch[I, B](fetch: Fetch[F, I, B])(i: I): F[DedupedFetch[F, Option[B]]] =
    fetch.singleDedupeCache(i)(unsafeCache)

  /** Fetch a set of values using this cache as a base. */
  def alsoFetchAll[I, B](is: Set[I])(implicit
      fetch: Fetch[F, I, B]
  ): F[DedupedFetch[F, Map[I, B]]] =
    fetch.batchDedupeCache(is)(unsafeCache)

  /** Fetch a set of values using this cache as a base. */
  def alsoFetchAll[I, B](fetch: Fetch[F, I, B])(is: Set[I]): F[DedupedFetch[F, Map[I, B]]] =
    fetch.batchDedupeCache(is)(unsafeCache)

  /**
   * Fold another deduped fetch request into this one, so that the cache will contain values from
   * both fetches.
   */
  def absorb[B](df: DedupedFetch[F, B]): DedupedFetch[F, B] =
    df.copy(unsafeCache = unsafeCache ++ df.unsafeCache)

  /**
   * Filter the deduped cache results by the selected `Fetch` instance. If any results exist for the
   * instance supplied, they will be presented in a new map.
   */
  def access[I, B](implicit fetch: Fetch[F, I, B]) = unsafeCache.collect[I, B] {
    case ((i, fid), Some(b)) if (fid == fetch.id) =>
      i.asInstanceOf[I] -> b.asInstanceOf[B]
  }
}

object DedupedFetch {

  def prepopulated[F[_]: Applicative](cache: CacheMap) = DedupedFetch[F, Unit](cache, ())

  def empty[F[_]: Applicative] = prepopulated(Map.empty)

  /** `cats.Monad` instance for `DedupedFetch` */
  implicit def dedupedFetchM[F[_]: Monad]: Monad[DedupedFetch[F, *]] =
    new Monad[DedupedFetch[F, *]] {
      def flatMap[A, B](fa: DedupedFetch[F, A])(f: A => DedupedFetch[F, B]): DedupedFetch[F, B] =
        fa.absorb(f(fa.last))

      def tailRecM[A, B](a: A)(f: A => DedupedFetch[F, Either[A, B]]): DedupedFetch[F, B] = {
        val fab = f(a)
        fab.last match {
          case Left(value)  => tailRecM(value)(f)
          case Right(value) => fab.copy(last = value)
        }
      }

      def pure[A](x: A): DedupedFetch[F, A] = DedupedFetch(Map.empty, x)

    }

  implicit def dedupedFetchA[F[_]: Applicative]: Applicative[DedupedFetch[F, *]] =
    new Applicative[DedupedFetch[F, *]] {
      def ap[A, B](ff: DedupedFetch[F, A => B])(fa: DedupedFetch[F, A]): DedupedFetch[F, B] =
        ff.absorb(fa.copy(last = ff.last(fa.last)))

      def pure[A](x: A): DedupedFetch[F, A] = DedupedFetch[F, A](Map.empty, x)

    }

  implicit def dedupedFetchF[F[_]: Functor]: Functor[DedupedFetch[F, *]] =
    new Functor[DedupedFetch[F, *]] {
      def map[A, B](fa: DedupedFetch[F, A])(f: A => B): DedupedFetch[F, B] =
        fa.copy(last = f(fa.last))
    }
}
