package fetchless

import cats.Functor
import cats.syntax.all._
import cats.Applicative
import cats.data.Chain
import cats.FlatMap
import cats.Monad
import cats.Parallel
import cats.Traverse
import cats.data.Validated
import cats.CommutativeApplicative
import cats.data.Kleisli
import LazyBatch.LazyBatchReqs

final case class LazyBatch[F[_]: Applicative, A](
    reqs: LazyBatchReqs[F],
    getResult: Kleisli[F, CacheMap, DedupedFetch[F, A]]
) {

  def absorb[B](lbn: LazyBatch[F, B]) = {
    val newReqs = lbn.reqs.toList.map { case (k, (set, f)) =>
      reqs.get(k) match {
        case Some((ids, f)) => (k, (set ++ ids, f))
        case None           => (k, (set, f))
      }
    }.toMap
    val combinedReqs = reqs ++ newReqs
    LazyBatch(
      reqs = combinedReqs,
      getResult = lbn.getResult
    )
  }

  def alsoOne[I, B](fetch: Fetch[F, I, B])(i: I): LazyBatch[F, Option[B]] =
    reqs.get(fetch.id) match {
      case Some((is, f)) =>
        val nextIs = is + i
        LazyBatch(
          reqs + (fetch.id -> (nextIs, f)),
          Kleisli(c =>
            DedupedFetch
              .prepopulated[F](c)
              .as(c.get(i -> fetch.id).flatten.asInstanceOf[Option[B]])
              .pure[F]
          )
        )
      case None =>
        val batchAny: (Set[Any], CacheMap) => F[DedupedFetch[F, Map[Any, Any]]] = {
          case (anySet, cacheMap) =>
            val res = fetch.batchDedupeCache(anySet.asInstanceOf[Set[I]])(cacheMap)
            res.map(df => df.map(_.asInstanceOf[Map[Any, Any]]))
        }
        LazyBatch(
          reqs = Map(fetch.id -> (Set(i).asInstanceOf[Set[Any]] -> batchAny)),
          getResult = Kleisli(c =>
            DedupedFetch
              .prepopulated[F](c)
              .as(c.get(i -> fetch.id).flatten.asInstanceOf[Option[B]])
              .pure[F]
          )
        )
    }

  def alsoMany[I, B](fetch: Fetch[F, I, B])(newIs: Set[I]): LazyBatch[F, Map[I, B]] =
    reqs.get(fetch.id) match {
      case Some((is, f)) =>
        val nextIs = is ++ newIs
        LazyBatch(
          reqs = reqs + (fetch.id -> (nextIs, f)),
          getResult = Kleisli(c =>
            DedupedFetch
              .prepopulated[F](c)
              .as(
                c.toList
                  .collect { case ((i, fetch.id), v) =>
                    v.map(someV => i -> someV).asInstanceOf[Option[(I, B)]]
                  }
                  .flattenOption
                  .toMap
              )
              .pure[F]
          )
        )
      case None =>
        val batchAny: (Set[Any], CacheMap) => F[DedupedFetch[F, Map[Any, Any]]] = {
          case (anySet, cacheMap) =>
            val res = fetch.batchDedupeCache(anySet.asInstanceOf[Set[I]])(cacheMap)
            res.map(df => df.map(_.asInstanceOf[Map[Any, Any]]))
        }
        LazyBatch(
          reqs = Map(fetch.id -> (newIs.asInstanceOf[Set[Any]] -> batchAny)),
          getResult = Kleisli(c =>
            DedupedFetch
              .prepopulated(c)
              .as(
                c.toList
                  .collect { case ((i, fetch.id), v) =>
                    v.map(someV => i -> someV).asInstanceOf[Option[(I, B)]]
                  }
                  .flattenOption
                  .toMap
              )
              .pure[F]
          )
        )
    }

  def run(implicit F: Monad[F], P: Parallel[F]): F[DedupedFetch[F, A]] =
    LazyBatch.runInternal[F, A](getResult).apply(reqs, Map.empty)

  def runWithCache(cache: CacheMap)(implicit F: Monad[F], P: Parallel[F]): F[DedupedFetch[F, A]] =
    LazyBatch.runInternal[F, A](getResult).apply(reqs, cache)

  /**
   * Modify the action of fetching. Useful if you want to add custom logic such as timeouts or
   * logging based on the result of a single fetch.
   */
  def mod[B](f: F[DedupedFetch[F, A]] => F[DedupedFetch[F, B]]) = LazyBatch[F, B](
    reqs,
    Kleisli(cache => f(getResult.run(cache)))
  )

}

object LazyBatch {

  type LazyBatchGetter[F[_]] = (Set[Any], CacheMap) => F[DedupedFetch[F, Map[Any, Any]]]

  type LazyBatchReqs[F[_]] =
    Map[String, (Set[Any], LazyBatchGetter[F])]

  private def runInternal[F[_]: Monad: Parallel, A](
      getResult: Kleisli[F, CacheMap, DedupedFetch[F, A]]
  ): (LazyBatchReqs[F], CacheMap) => F[DedupedFetch[F, A]] = { case (reqs, cacheMap) =>
    val result = reqs.toList.parTraverse { case (_, (ids, f)) => f(ids, cacheMap) }
    result
      .map(
        _.foldLeft(
          DedupedFetch
            .prepopulated[F](cacheMap)
            .as(Map.empty[Any, Any])
        ) { case (df, next) =>
          df.absorb(next)
        }.unsafeCache
      )
      .flatMap(getResult.run)
  }

  def liftF[F[_]: Monad, A](fa: F[A]): LazyBatch[F, A] = LazyBatch(
    Map.empty,
    Kleisli.liftF(fa.map(a => DedupedFetch.empty[F].as(a)))
  )

  def single[F[_]: Monad, I, A](fetch: Fetch[F, I, A])(i: I) = {
    val batchAny: (Set[Any], CacheMap) => F[DedupedFetch[F, Map[Any, Any]]] = {
      case (anySet, cacheMap) =>
        val res = fetch.batchDedupeCache(anySet.asInstanceOf[Set[I]])(cacheMap)
        res.map { df =>
          df.map(_.asInstanceOf[Map[Any, Any]])
        }
    }
    LazyBatch[F, Option[A]](
      Map(
        fetch.id -> (Set[Any](i), batchAny)
      ),
      Kleisli(c =>
        DedupedFetch
          .prepopulated[F](c)
          .as(c.get(i -> fetch.id).flatten.asInstanceOf[Option[A]])
          .pure[F]
      )
    )
  }

  def many[F[_]: Monad, I, A](fetch: Fetch[F, I, A])(is: Set[I]) = {
    val batchAny: (Set[Any], CacheMap) => F[DedupedFetch[F, Map[Any, Any]]] = {
      case (anySet, cacheMap) =>
        val res = fetch.batchDedupeCache(anySet.asInstanceOf[Set[I]])(cacheMap)
        res.map(df => df.map(_.asInstanceOf[Map[Any, Any]]))
    }
    LazyBatch(
      reqs = Map(fetch.id -> (is.asInstanceOf[Set[Any]] -> batchAny)),
      getResult = Kleisli(c =>
        DedupedFetch
          .prepopulated[F](c)
          .as(
            c.toList
              .collect {
                case ((i, fetch.id), v) if (is.asInstanceOf[Set[Any]].contains(i)) =>
                  v.map(someV => i -> someV).asInstanceOf[Option[(I, A)]]
              }
              .flattenOption
              .toMap
          )
          .pure[F]
      )
    )
  }

  implicit def lazyBatchA[F[_]: Monad: Parallel]: CommutativeApplicative[
    LazyBatch[F, *]
  ] =
    new CommutativeApplicative[LazyBatch[F, *]] {
      def ap[A, B](ff: LazyBatch[F, A => B])(fa: LazyBatch[F, A]): LazyBatch[F, B] = {
        val absorbed = fa.absorb(ff)
        LazyBatch(
          absorbed.reqs,
          ff.getResult.flatMap { df =>
            fa.getResult.map(ndf => df.absorb(ndf).map(df.last))
          }
        )
      }

      def pure[A](x: A): LazyBatch[F, A] =
        LazyBatch(
          Map.empty,
          Kleisli(c => DedupedFetch.prepopulated[F](c).absorb(DedupedFetch.empty[F].as(x)).pure[F])
        )

    }
}
