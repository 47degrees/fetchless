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
import LazyBatchRequest.LazyBatchRequestReqs

final case class LazyBatchRequest[F[_], A](
    reqs: LazyBatchRequestReqs[F],
    getResult: Kleisli[F, CacheMap, DedupedRequest[F, A]]
) {

  def absorb[B](lbn: LazyBatchRequest[F, B])(implicit F: Applicative[F]) = {
    val newReqs = lbn.reqs.toList.map { case (k, (set, f)) =>
      reqs.get(k) match {
        case Some((ids, f)) => (k, (set ++ ids, f))
        case None           => (k, (set, f))
      }
    }.toMap
    val combinedReqs = reqs ++ newReqs
    LazyBatchRequest(
      reqs = combinedReqs,
      getResult = lbn.getResult
    )
  }

  def alsoOne[I, B](
      fetch: Fetch[F, I, B]
  )(i: I)(implicit F: Applicative[F]): LazyBatchRequest[F, Option[B]] =
    reqs.get(fetch.id) match {
      case Some((is, f)) =>
        val nextIs = is + i
        LazyBatchRequest(
          reqs + (fetch.id -> (nextIs, f)),
          Kleisli(c =>
            DedupedRequest
              .prepopulated[F](c)
              .as(c.get(i -> fetch.id).flatten.asInstanceOf[Option[B]])
              .pure[F]
          )
        )
      case None =>
        val batchAny: (Set[Any], CacheMap) => F[DedupedRequest[F, Map[Any, Any]]] = {
          case (anySet, cacheMap) =>
            val res = fetch.batchDedupeCache(anySet.asInstanceOf[Set[I]])(cacheMap)
            res.map(df => df.map(_.asInstanceOf[Map[Any, Any]]))
        }
        LazyBatchRequest(
          reqs = Map(fetch.id -> (Set(i).asInstanceOf[Set[Any]] -> batchAny)),
          getResult = Kleisli(c =>
            DedupedRequest
              .prepopulated[F](c)
              .as(c.get(i -> fetch.id).flatten.asInstanceOf[Option[B]])
              .pure[F]
          )
        )
    }

  def alsoMany[I, B](
      fetch: Fetch[F, I, B]
  )(newIs: Set[I])(implicit F: Applicative[F]): LazyBatchRequest[F, Map[I, B]] =
    reqs.get(fetch.id) match {
      case Some((is, f)) =>
        val nextIs = is ++ newIs
        LazyBatchRequest(
          reqs = reqs + (fetch.id -> (nextIs, f)),
          getResult = Kleisli(c =>
            DedupedRequest
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
        val batchAny: (Set[Any], CacheMap) => F[DedupedRequest[F, Map[Any, Any]]] = {
          case (anySet, cacheMap) =>
            val res = fetch.batchDedupeCache(anySet.asInstanceOf[Set[I]])(cacheMap)
            res.map(df => df.map(_.asInstanceOf[Map[Any, Any]]))
        }
        LazyBatchRequest(
          reqs = Map(fetch.id -> (newIs.asInstanceOf[Set[Any]] -> batchAny)),
          getResult = Kleisli(c =>
            DedupedRequest
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
    }

  def run(implicit F: Monad[F], P: Parallel[F]): F[DedupedRequest[F, A]] =
    LazyBatchRequest.runInternal[F, A](getResult).apply(reqs, Map.empty)

  def runWithCache(cache: CacheMap)(implicit F: Monad[F], P: Parallel[F]): F[DedupedRequest[F, A]] =
    LazyBatchRequest.runInternal[F, A](getResult).apply(reqs, cache)

}

object LazyBatchRequest {

  type LazyBatchRequestGetter[F[_]] = (Set[Any], CacheMap) => F[DedupedRequest[F, Map[Any, Any]]]

  type LazyBatchRequestReqs[F[_]] =
    Map[String, (Set[Any], LazyBatchRequestGetter[F])]

  private def runInternal[F[_]: Monad: Parallel, A](
      getResult: Kleisli[F, CacheMap, DedupedRequest[F, A]]
  ): (LazyBatchRequestReqs[F], CacheMap) => F[DedupedRequest[F, A]] = { case (reqs, cacheMap) =>
    val result = reqs.toList.parTraverse { case (_, (ids, f)) => f(ids, cacheMap) }
    result
      .map(
        _.foldLeft(
          DedupedRequest
            .prepopulated[F](cacheMap)
            .as(Map.empty[Any, Any])
        ) { case (df, next) =>
          df.absorb(next)
        }.unsafeCache
      )
      .flatMap(getResult.run)
  }

  def liftF[F[_]: Applicative, A](fa: F[A]): LazyBatchRequest[F, A] = LazyBatchRequest(
    Map.empty,
    Kleisli.liftF(fa.map(a => DedupedRequest.empty[F].as(a)))
  )

  def single[F[_]: Monad, I, A](fetch: Fetch[F, I, A])(i: I) = {
    val batchAny: (Set[Any], CacheMap) => F[DedupedRequest[F, Map[Any, Any]]] = {
      case (anySet, cacheMap) =>
        val res = fetch.batchDedupeCache(anySet.asInstanceOf[Set[I]])(cacheMap)
        res.map { df =>
          df.map(_.asInstanceOf[Map[Any, Any]])
        }
    }
    LazyBatchRequest[F, Option[A]](
      Map(
        fetch.id -> (Set[Any](i), batchAny)
      ),
      Kleisli(c =>
        DedupedRequest
          .prepopulated[F](c)
          .as(c.get(i -> fetch.id).flatten.asInstanceOf[Option[A]])
          .pure[F]
      )
    )
  }

  def many[F[_]: Monad, I, A](fetch: Fetch[F, I, A])(is: Set[I]) = {
    val batchAny: (Set[Any], CacheMap) => F[DedupedRequest[F, Map[Any, Any]]] = {
      case (anySet, cacheMap) =>
        val res = fetch.batchDedupeCache(anySet.asInstanceOf[Set[I]])(cacheMap)
        res.map(df => df.map(_.asInstanceOf[Map[Any, Any]]))
    }
    LazyBatchRequest(
      reqs = Map(fetch.id -> (is.asInstanceOf[Set[Any]] -> batchAny)),
      getResult = Kleisli(c =>
        DedupedRequest
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

  implicit def lazyBatchRequestA[F[_]: Monad]: CommutativeApplicative[
    LazyBatchRequest[F, *]
  ] =
    new CommutativeApplicative[LazyBatchRequest[F, *]] {
      def ap[A, B](
          ff: LazyBatchRequest[F, A => B]
      )(fa: LazyBatchRequest[F, A]): LazyBatchRequest[F, B] = {
        val absorbed = fa.absorb(ff)
        LazyBatchRequest(
          absorbed.reqs,
          ff.getResult.flatMap { df =>
            fa.getResult.map(ndf => df.absorb(ndf).map(df.last))
          }
        )
      }

      def pure[A](x: A): LazyBatchRequest[F, A] =
        LazyBatchRequest(
          Map.empty,
          Kleisli(c =>
            DedupedRequest.prepopulated[F](c).absorb(DedupedRequest.empty[F].as(x)).pure[F]
          )
        )

    }

  implicit def lazyBatchRequestF[F[_]: Applicative]: Functor[
    LazyBatchRequest[F, *]
  ] =
    new Functor[LazyBatchRequest[F, *]] {

      def map[A, B](fa: LazyBatchRequest[F, A])(f: A => B): LazyBatchRequest[F, B] =
        LazyBatchRequest(
          fa.reqs,
          fa.getResult.map(df => df.map(f))
        )
    }
}
