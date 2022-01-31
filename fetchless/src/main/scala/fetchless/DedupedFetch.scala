package fetchless

import cats.Applicative
import cats.syntax.all._
import cats.FlatMap
import cats.Monad

/**
 * A fetch that was ran, along with a local cache of other recent fetches done in the current
 * sequence, for deduping.
 */
final case class DedupedFetch[F[_], A](cache: Map[(Any, String), Option[Any]], last: A) {

  /** Fetch another value using this cache as a base. */
  def alsoFetch[I, B](
      i: I
  )(implicit fetch: Fetch[F, I, B], f: Monad[F]): F[DedupedFetch[F, Option[B]]] =
    fetch.singleDedupeCache(i)(cache)

  /** Fetch a set of values using this cache as a base. */
  def alsoFetchAll[I, B](
      is: Set[I]
  )(implicit fetch: Fetch[F, I, B], f: Monad[F]): F[DedupedFetch[F, Map[I, B]]] =
    fetch.batchDedupeCache(is)(cache)

  /**
   * Fold another fetch cache into this one, so that the cache will contain values from both
   * fetches.
   */
  def absorb[B](df: DedupedFetch[F, B]): DedupedFetch[F, B] = df.copy(cache = cache ++ df.cache)
}

object DedupedFetch {
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
}
