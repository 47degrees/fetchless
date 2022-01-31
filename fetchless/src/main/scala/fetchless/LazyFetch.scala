package fetchless

import cats.Eval
import cats.data.Kleisli
import cats.Monad
import cats.syntax.all._
import cats.Functor
import cats.FlatMap

final case class LazyFetch[F[_]: Monad, A](k: Kleisli[F, CacheMap, DedupedFetch[F, A]]) {
  def andThenFetch[I, B](i: I)(implicit fetch: Fetch[F, I, B]): LazyFetch[F, Option[B]] = LazyFetch(
    k.andThen { df =>
      fetch.singleDedupeCache(i)(df.cache)
    }
  )

  def andThenBatch[I, B](
      is: Set[I]
  )(implicit fetch: Fetch[F, I, B]): LazyFetch[F, Map[I, B]] = LazyFetch(
    k.andThen { df =>
      fetch.batchDedupeCache(is)(df.cache)
    }
  )

  def preFetch[I, B](
      i: I
  )(implicit fetch: Fetch[F, I, B]): LazyFetch[F, A] = LazyFetch(
    Kleisli { cache =>
      fetch.singleDedupeCache(i)(cache)
    }.andThen { df =>
      k.run(df.cache)
    }
  )

  def preBatch[I, B](
      is: Set[I]
  )(implicit fetch: Fetch[F, I, B]): LazyFetch[F, A] = LazyFetch(
    Kleisli { cache =>
      fetch.batchDedupeCache(is)(cache)
    }.andThen { df =>
      k.run(df.cache)
    }
  )

  def run: F[DedupedFetch[F, A]] = k.run(Map.empty)
}

object LazyFetch {
  def liftF[F[_]: Monad, A](fa: F[A]): LazyFetch[F, A] = LazyFetch(
    Kleisli(c => fa.map(a => DedupedFetch(c, a)))
  )
  implicit def lazyFetchM[F[_]: Monad] = new Monad[LazyFetch[F, *]] {
    def flatMap[A, B](fa: LazyFetch[F, A])(f: A => LazyFetch[F, B]): LazyFetch[F, B] =
      LazyFetch(fa.k.flatMap { df =>
        Kleisli { _ =>
          f(df.last).k.run(df.cache)
        }
      })

    def tailRecM[A, B](a: A)(f: A => LazyFetch[F, Either[A, B]]): LazyFetch[F, B] =
      LazyFetch(
        a.tailRecM[Kleisli[F, CacheMap, *], B](x => f(x).k.map(_.last))
          .map(_.pure[DedupedFetch[F, *]])
      )

    def pure[A](x: A): LazyFetch[F, A] = LazyFetch(Kleisli(c => DedupedFetch[F, A](c, x).pure[F]))

  }
}
