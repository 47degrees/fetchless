package fetchless

import cats.Applicative
import cats.Eval
import cats.FlatMap
import cats.Functor
import cats.Monad
import cats.Parallel
import cats.arrow.FunctionK
import cats.data.Kleisli
import cats.syntax.all._
import cats.~>

/**
 * A `DedupedFetch` that has not been executed yet. Can be chained with other `LazyFetch` values
 * forwards and backwards before executing.
 */
final case class LazyFetch[F[_]: FlatMap, A](k: Kleisli[F, CacheMap, DedupedFetch[F, A]]) {

  /** Execute a single fetch after the current `LazyFetch`, deduping as necessary. */
  def andThenFetch[I, B](i: I)(implicit fetch: Fetch[F, I, B]): LazyFetch[F, Option[B]] = LazyFetch(
    k.andThen { df =>
      fetch.singleDedupeCache(i)(df.unsafeCache)
    }
  )

  /** Execute a single fetch after the current `LazyFetch`, deduping as necessary. */
  def andThenFetch[I, B](fetch: Fetch[F, I, B])(i: I): LazyFetch[F, Option[B]] = LazyFetch(
    k.andThen { df =>
      fetch.singleDedupeCache(i)(df.unsafeCache)
    }
  )

  /**
   * Execute a batch fetch after the current `LazyFetch`. The resulting `LazyFetch` will execute
   * this batch request in sequence following the previous one, deduping any fetches as-needed along
   * the way.
   */
  def andThenBatch[I, B](is: Set[I])(implicit fetch: Fetch[F, I, B]): LazyFetch[F, Map[I, B]] =
    LazyFetch(
      k.andThen { df =>
        fetch.batchDedupeCache(is)(df.unsafeCache)
      }
    )

  /**
   * Execute a batch fetch after the current `LazyFetch`. The resulting `LazyFetch` will execute
   * this batch request in sequence following the previous one, deduping any fetches as-needed along
   * the way.
   */
  def andThenBatch[I, B](fetch: Fetch[F, I, B])(is: Set[I]): LazyFetch[F, Map[I, B]] = LazyFetch(
    k.andThen { df =>
      fetch.batchDedupeCache(is)(df.unsafeCache)
    }
  )

  /**
   * Execute a single fetch before this fetch occurs. Ensures that the result will appear in the
   * resulting dedupe cache.
   *
   * You will likely not need to use this unless there is a specific order-of-operations concern you
   * are accounting for. For most cases, you should just explicitly `flatMap` multiple `LazyFetch`
   * values in sequence.
   */
  def preFetch[I, B](i: I)(implicit fetch: Fetch[F, I, B]): LazyFetch[F, A] = LazyFetch(
    Kleisli { cache =>
      fetch.singleDedupeCache(i)(cache)
    }.andThen { df =>
      k.run(df.unsafeCache)
    }
  )

  /**
   * Execute a single fetch before this fetch occurs. Ensures that the result will appear in the
   * resulting dedupe cache.
   *
   * You will likely not need to use this unless there is a specific order-of-operations concern you
   * are accounting for. For most cases, you should just explicitly `flatMap` multiple `LazyFetch`
   * values in sequence.
   */
  def preFetch[I, B](fetch: Fetch[F, I, B])(i: I): LazyFetch[F, A] = LazyFetch(
    Kleisli { cache =>
      fetch.singleDedupeCache(i)(cache)
    }.andThen { df =>
      k.run(df.unsafeCache)
    }
  )

  /**
   * Execute a batch fetch before this fetch occurs. Can help dedupe singular fetches that might
   * have been composed together already.
   */
  def preBatch[I, B](is: Set[I])(implicit fetch: Fetch[F, I, B]): LazyFetch[F, A] = LazyFetch(
    Kleisli { cache =>
      fetch.batchDedupeCache(is)(cache)
    }.andThen { df =>
      k.run(df.unsafeCache)
    }
  )

  /**
   * Execute a batch fetch before this fetch occurs. Can help dedupe singular fetches that might
   * have been composed together already.
   */
  def preBatch[I, B](fetch: Fetch[F, I, B])(is: Set[I]): LazyFetch[F, A] = LazyFetch(
    Kleisli { cache =>
      fetch.batchDedupeCache(is)(cache)
    }.andThen { df =>
      k.run(df.unsafeCache)
    }
  )

  /** Executes this `LazyFetch`, returning a `DedupedFetch` that has already ran. */
  def run: F[DedupedFetch[F, A]] = k.run(Map.empty)
}

object LazyFetch {

  /**
   * Lift any effectful value `F[A]` into a `LazyFetch` context, so you can use it in `flatMap`
   * chains and for-comprehensions.
   */
  def liftF[F[_]: FlatMap, A](fa: F[A]): LazyFetch[F, A] = LazyFetch(
    Kleisli(c => fa.map(a => DedupedFetch(c, a)))
  )

  /** `cats.Monad` instance for `LazyFetch` */
  implicit def lazyFetchM[F[_]: Monad] = new Monad[LazyFetch[F, *]] {
    def flatMap[A, B](fa: LazyFetch[F, A])(f: A => LazyFetch[F, B]): LazyFetch[F, B] =
      LazyFetch(fa.k.flatMap { df =>
        Kleisli { _ =>
          f(df.last).k.run(df.unsafeCache)
        }
      })

    def tailRecM[A, B](a: A)(f: A => LazyFetch[F, Either[A, B]]): LazyFetch[F, B] =
      LazyFetch(
        a.tailRecM[Kleisli[F, CacheMap, *], B](x => f(x).k.map(_.last))
          .map(_.pure[DedupedFetch[F, *]])
      )

    def pure[A](x: A): LazyFetch[F, A] = LazyFetch(Kleisli(c => DedupedFetch[F, A](c, x).pure[F]))

  }

  implicit def lazyFetchP[M[_]: Monad: Parallel] = new Parallel[LazyFetch[M, *]] {

    type F[x] = LazyBatch[M, x]

    def sequential: LazyBatch[M, *] ~> LazyFetch[M, *] =
      new FunctionK[LazyBatch[M, *], LazyFetch[M, *]] {
        def apply[A](l: LazyBatch[M, A]): LazyFetch[M, A] = LazyFetch(
          Kleisli { cache =>
            l.runWithCache(cache)
          }
        )
      }

    def parallel: LazyFetch[M, *] ~> LazyBatch[M, *] =
      new FunctionK[LazyFetch[M, *], LazyBatch[M, *]] {
        def apply[A](l: LazyFetch[M, A]): LazyBatch[M, A] =
          LazyBatch(
            Map.empty,
            Kleisli(c => l.k.run(c))
          )
      }

    def applicative: Applicative[LazyBatch[M, *]] = LazyBatch.lazyBatchA[M]

    def monad: Monad[LazyFetch[M, *]] = lazyFetchM[M]

  }
}
