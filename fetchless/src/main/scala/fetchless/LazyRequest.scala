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
 * A `DedupedRequest` that has not been executed yet. Can be chained with other `LazyRequest` values
 * forwards and backwards before executing.
 */
final case class LazyRequest[F[_], A](k: Kleisli[F, CacheMap, DedupedRequest[F, A]]) {

  /** Execute a single fetch after the current `LazyRequest`, deduping as necessary. */
  def andThenFetch[I, B](
      i: I
  )(implicit fetch: Fetch[F, I, B], F: FlatMap[F]): LazyRequest[F, Option[B]] = LazyRequest(
    k.andThen { df =>
      fetch.singleDedupeCache(i)(df.unsafeCache)
    }
  )

  /** Execute a single fetch after the current `LazyRequest`, deduping as necessary. */
  def andThenFetch[I, B](
      fetch: Fetch[F, I, B]
  )(i: I)(implicit F: FlatMap[F]): LazyRequest[F, Option[B]] = LazyRequest(
    k.andThen { df =>
      fetch.singleDedupeCache(i)(df.unsafeCache)
    }
  )

  /**
   * Execute a batch fetch after the current `LazyRequest`. The resulting `LazyRequest` will execute
   * this batch request in sequence following the previous one, deduping any fetches as-needed along
   * the way.
   */
  def andThenBatch[I, B](
      is: Set[I]
  )(implicit fetch: Fetch[F, I, B], F: FlatMap[F]): LazyRequest[F, Map[I, B]] =
    LazyRequest(
      k.andThen { df =>
        fetch.batchDedupeCache(is)(df.unsafeCache)
      }
    )

  /**
   * Execute a batch fetch after the current `LazyRequest`. The resulting `LazyRequest` will execute
   * this batch request in sequence following the previous one, deduping any fetches as-needed along
   * the way.
   */
  def andThenBatch[I, B](
      fetch: Fetch[F, I, B]
  )(is: Set[I])(implicit F: FlatMap[F]): LazyRequest[F, Map[I, B]] = LazyRequest(
    k.andThen { df =>
      fetch.batchDedupeCache(is)(df.unsafeCache)
    }
  )

  /**
   * Execute a single fetch before this fetch occurs. Ensures that the result will appear in the
   * resulting dedupe cache.
   *
   * You will likely not need to use this unless there is a specific order-of-operations concern you
   * are accounting for. For most cases, you should just explicitly `flatMap` multiple `LazyRequest`
   * values in sequence.
   */
  def preFetch[I, B](i: I)(implicit fetch: Fetch[F, I, B], F: FlatMap[F]): LazyRequest[F, A] =
    LazyRequest(
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
   * are accounting for. For most cases, you should just explicitly `flatMap` multiple `LazyRequest`
   * values in sequence.
   */
  def preFetch[I, B](fetch: Fetch[F, I, B])(i: I)(implicit F: FlatMap[F]): LazyRequest[F, A] =
    LazyRequest(
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
  def preBatch[I, B](is: Set[I])(implicit fetch: Fetch[F, I, B], F: FlatMap[F]): LazyRequest[F, A] =
    LazyRequest(
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
  def preBatch[I, B](fetch: Fetch[F, I, B])(is: Set[I])(implicit F: FlatMap[F]): LazyRequest[F, A] =
    LazyRequest(
      Kleisli { cache =>
        fetch.batchDedupeCache(is)(cache)
      }.andThen { df =>
        k.run(df.unsafeCache)
      }
    )

  /** Executes this `LazyRequest`, returning a `DedupedRequest` that has already ran. */
  def run: F[DedupedRequest[F, A]] = k.run(Map.empty)

}

object LazyRequest {

  /**
   * Lift any effectful value `F[A]` into a `LazyRequest` context, so you can use it in `flatMap`
   * chains and for-comprehensions.
   */
  def liftF[F[_]: FlatMap, A](fa: F[A]): LazyRequest[F, A] = LazyRequest(
    Kleisli(c => fa.map(a => DedupedRequest(c, a)))
  )

  /** `cats.Monad` instance for `LazyRequest` */
  implicit def lazyRequestM[F[_]: Monad] = new Monad[LazyRequest[F, *]] {
    def flatMap[A, B](fa: LazyRequest[F, A])(f: A => LazyRequest[F, B]): LazyRequest[F, B] =
      LazyRequest(fa.k.flatMap { df =>
        Kleisli { _ =>
          f(df.last).k.run(df.unsafeCache)
        }
      })

    def tailRecM[A, B](a: A)(f: A => LazyRequest[F, Either[A, B]]): LazyRequest[F, B] =
      LazyRequest(
        a.tailRecM[Kleisli[F, CacheMap, *], B](x => f(x).k.map(_.last))
          .map(_.pure[DedupedRequest[F, *]])
      )

    def pure[A](x: A): LazyRequest[F, A] = LazyRequest(
      Kleisli(c => DedupedRequest[F, A](c, x).pure[F])
    )

  }

  implicit def lazyRequestF[F[_]: Functor] = new Functor[LazyRequest[F, *]] {
    def map[A, B](fa: LazyRequest[F, A])(f: A => B): LazyRequest[F, B] = LazyRequest(
      fa.k.map(_.map(f))
    )

  }

  implicit def lazyRequestP[M[_]: Monad: Parallel] = new Parallel[LazyRequest[M, *]] {

    type F[x] = LazyBatchRequest[M, x]

    def sequential: LazyBatchRequest[M, *] ~> LazyRequest[M, *] =
      new FunctionK[LazyBatchRequest[M, *], LazyRequest[M, *]] {
        def apply[A](l: LazyBatchRequest[M, A]): LazyRequest[M, A] = LazyRequest(
          Kleisli { cache =>
            l.runWithCache(cache)
          }
        )
      }

    def parallel: LazyRequest[M, *] ~> LazyBatchRequest[M, *] =
      new FunctionK[LazyRequest[M, *], LazyBatchRequest[M, *]] {
        def apply[A](l: LazyRequest[M, A]): LazyBatchRequest[M, A] =
          LazyBatchRequest(
            Map.empty,
            Kleisli(c => l.k.run(c))
          )
      }

    def applicative: Applicative[LazyBatchRequest[M, *]] = LazyBatchRequest.lazyBatchRequestA[M]

    def monad: Monad[LazyRequest[M, *]] = lazyRequestM[M]

  }
}
