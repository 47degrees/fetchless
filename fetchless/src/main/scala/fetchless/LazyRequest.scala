package fetchless

import cats._
import cats.arrow.FunctionK
import cats.data.Kleisli
import cats.syntax.all._
import cats.~>
import fetchless.LazyRequest.FetchReqInfo
import fetchless.LazyRequest.LiftedReqInfo
import cats.data.Chain
import fs2.Chunk
import fetchless.LazyRequest.AllReqInfo

final case class LazyRequest[F[_], A](
    k: Kleisli[F, FetchCache, LazyRequest.ReqInfo[F, A]]
) {
  def flatMap[B](f: A => LazyRequest[F, B])(implicit F: FlatMap[F]) = LazyRequest[F, B](
    Kleisli { c =>
      k.run(c).flatMap { info =>
        info.run.flatMap { df =>
          f(df.last).k.run(df.unsafeCache).map { twoI =>
            twoI.updateCache(df.unsafeCache)
          }
        }
      }
    }
  )
  def run(implicit F: FlatMap[F]): F[DedupedRequest[F, A]] =
    k.run(FetchCache.empty)
      .flatMap { i =>
        i.run
      }

  def toBatch(implicit F: Monad[F]): LazyBatchRequest[F, A] =
    LazyBatchRequest(
      k.map { reqInfo =>
        reqInfo match {
          case FetchReqInfo(
                prevCache,
                fetchId,
                reqId,
                isBatch,
                doSingle,
                doBatch,
                getResultK,
                mapTo
              ) =>
            if (isBatch) {
              LazyBatchRequest.BReqInfo(
                prevCache,
                Map.empty,
                Map(fetchId -> (reqId.asInstanceOf[Set[Any]] -> { case (x, y) =>
                  doBatch(x, y).map(_.unsafeCache)
                })),
                Map.empty,
                getResultK
              )
            } else {
              LazyBatchRequest.BReqInfo(
                prevCache,
                Map.empty,
                Map(fetchId -> (Set(reqId) -> { case (x, y) =>
                  doBatch(x, y).map(_.unsafeCache)
                })),
                Map.empty,
                getResultK
              )
            }
          case LiftedReqInfo(prevCache, id, getResultK) =>
            LazyBatchRequest.BReqInfo(
              prevCache,
              Map.empty,
              Map.empty,
              Map(
                id -> getResultK.map(c => c.unsafeCache + ((id, FetchId.Lifted) -> Some(c.last)))
              ),
              Kleisli { fetchCache =>
                DedupedRequest[F, A](
                  fetchCache,
                  fetchCache.cacheMap.apply(id -> FetchId.Lifted).get.asInstanceOf[A]
                ).pure[F]
              }
            )
          case LazyRequest.PureReqInfo(prevCache, run) =>
            LazyBatchRequest.BReqInfo(
              prevCache,
              Map.empty,
              Map.empty,
              Map.empty,
              run
            )
          case AllReqInfo(prevCache, fetchId, runFetchAll, getResultK) =>
            LazyBatchRequest.BReqInfo(
              prevCache,
              Map(fetchId -> runFetchAll),
              Map.empty,
              Map.empty,
              getResultK
            )
        }
      }
    )
}

object LazyRequest {

  sealed trait ReqInfo[F[_], A] extends Product with Serializable {
    val prevCache: FetchCache
    val getResultK: Kleisli[F, FetchCache, DedupedRequest[F, A]]
    def updateCache(extra: FetchCache): ReqInfo[F, A]
    def run(implicit F: Functor[F]): F[DedupedRequest[F, A]]
    def runCached(cache: FetchCache)(implicit F: Functor[F]): F[DedupedRequest[F, A]]
  }

  object ReqInfo {
    def fetch[F[_], A](
        prevCache: FetchCache,
        fetchId: FetchId.StringId,
        reqId: Any,
        isBatch: Boolean,
        doSingle: (Any, FetchCache) => F[DedupedRequest[F, Any]],
        doBatch: (Set[Any], FetchCache) => F[DedupedRequest[F, Map[Any, Any]]],
        getResultK: Kleisli[F, FetchCache, DedupedRequest[F, A]],
        mapTo: DedupedRequest[F, Any] => DedupedRequest[F, A]
    ): ReqInfo[F, A] = {
      FetchReqInfo(
        prevCache,
        fetchId,
        reqId,
        isBatch,
        doSingle,
        doBatch,
        getResultK,
        mapTo
      )
    }

    def lift[F[_]: Applicative, A](
        inCache: FetchCache,
        reqId: Any,
        fa: F[A]
    ): ReqInfo[F, A] =
      LiftedReqInfo(
        inCache,
        reqId,
        Kleisli[F, FetchCache, DedupedRequest[F, A]] { c =>
          c.cacheMap.get(reqId -> FetchId.Lifted) match {
            case None =>
              fa.map { a =>
                val newCache = c + ((reqId -> FetchId.Lifted), Some(a))
                DedupedRequest(newCache, a)
              }
            case Some(value) =>
              DedupedRequest(c, value.get).pure[F].asInstanceOf[F[DedupedRequest[F, A]]]
          }
        }
      )

    /** Variant that overrides the batch function to get every result */
    def fetchAll[F[_], A](
        inCache: FetchCache,
        fetchId: FetchId.StringId,
        runFetchAll: F[DedupedRequest[F, Any]],
        getResultK: Kleisli[F, FetchCache, DedupedRequest[F, A]]
    ): ReqInfo[F, A] =
      AllReqInfo(
        inCache,
        fetchId,
        runFetchAll,
        getResultK
      )

    def pure[F[_]: Applicative, A](
        inCache: FetchCache,
        a: A
    ): ReqInfo[F, A] =
      PureReqInfo(
        inCache,
        Kleisli[F, FetchCache, DedupedRequest[F, A]] { c =>
          DedupedRequest(c, a).pure[F]
        }
      )
  }
  final case class FetchReqInfo[F[_], A](
      prevCache: FetchCache,
      fetchId: FetchId.StringId,
      reqId: Any,
      isBatch: Boolean,
      doSingle: (Any, FetchCache) => F[DedupedRequest[F, Any]],
      doBatch: (Set[Any], FetchCache) => F[DedupedRequest[F, Map[Any, Any]]],
      getResultK: Kleisli[F, FetchCache, DedupedRequest[F, A]],
      mapTo: DedupedRequest[F, Any] => DedupedRequest[F, A] // Maps the final result
  ) extends ReqInfo[F, A] {
    def updateCache(extra: FetchCache): ReqInfo[F, A] = copy(prevCache = prevCache ++ extra)
    def runCached(c: FetchCache)(implicit F: Functor[F]): F[DedupedRequest[F, A]] =
      if (isBatch)
        doBatch(reqId.asInstanceOf[Set[Any]], c)
          .map { df =>
            mapTo(df.asInstanceOf[DedupedRequest[F, Any]])
          }
      else doSingle(reqId, c).map(mapTo)
    def run(implicit F: Functor[F]): F[DedupedRequest[F, A]] = runCached(prevCache)
  }
  final case class LiftedReqInfo[F[_], A](
      prevCache: FetchCache,
      reqId: Any, // Used in the event of needing to combine requests into a batch
      getResultK: Kleisli[F, FetchCache, DedupedRequest[F, A]]
  ) extends ReqInfo[F, A] {
    val fetchId                                       = FetchId.Lifted
    def updateCache(extra: FetchCache): ReqInfo[F, A] = copy(prevCache = prevCache ++ extra)
    def runCached(c: FetchCache)(implicit F: Functor[F]): F[DedupedRequest[F, A]] = {
      getResultK(c).map(df =>
        df.copy(unsafeCache = df.unsafeCache + ((reqId, FetchId.Lifted), Some(df.last)))
      )
    }
    def run(implicit F: Functor[F]): F[DedupedRequest[F, A]] =
      runCached(prevCache)
  }
  final case class AllReqInfo[F[_], A](
      prevCache: FetchCache,
      fetchId: FetchId.StringId,
      runFetchAll: Kleisli[F, FetchCache, DedupedRequest[F, Any]],
      getResultK: Kleisli[F, FetchCache, DedupedRequest[F, A]]
  ) extends ReqInfo[F, A] {
    def updateCache(extra: FetchCache): ReqInfo[F, A] = copy(prevCache = prevCache ++ extra)
    def runCached(c: FetchCache)(implicit F: Functor[F]): F[DedupedRequest[F, A]] =
      runFetchAll(c).asInstanceOf[F[DedupedRequest[F, A]]]
    def run(implicit F: Functor[F]): F[DedupedRequest[F, A]] =
      runCached(prevCache)
  }
  final case class PureReqInfo[F[_], A](
      prevCache: FetchCache,
      getResultK: Kleisli[F, FetchCache, DedupedRequest[F, A]]
  ) extends ReqInfo[F, A] {
    def updateCache(extra: FetchCache): ReqInfo[F, A] = copy(prevCache = prevCache ++ extra)
    def runCached(c: FetchCache)(implicit F: Functor[F]): F[DedupedRequest[F, A]] = getResultK(c)
    def run(implicit F: Functor[F]): F[DedupedRequest[F, A]] = runCached(prevCache)
  }

  /**
   * Lift any effectful value `F[A]` into a `LazyRequest` context, so you can use it in `flatMap`
   * chains and for-comprehensions.
   */
  def liftF[F[_]: Applicative, A](fa: F[A])(id: Any): LazyRequest[F, A] = LazyRequest(
    Kleisli(c => ReqInfo.lift(c, id, fa).pure[F])
  )

  /** `cats.Monad` instance for `LazyRequest` */
  implicit def lazyRequestM[F[_]: Monad] = new Monad[LazyRequest[F, *]] {
    def flatMap[A, B](fa: LazyRequest[F, A])(f: A => LazyRequest[F, B]): LazyRequest[F, B] =
      fa.flatMap(f)
    def tailRecM[A, B](a: A)(f: A => LazyRequest[F, Either[A, B]]): LazyRequest[F, B] =
      LazyRequest(
        a.tailRecM[Kleisli[F, FetchCache, *], B] { x =>
          f(x).k
            .flatMap { reqInfo =>
              Kleisli.liftF(reqInfo.run)
            }
            .map(_.last)
        }.flatMap { b =>
          pure(b).k
        }
      )
    def pure[A](x: A): LazyRequest[F, A] = LazyRequest(
      Kleisli(c => ReqInfo.pure(c, x).pure[F])
    )
  }
  implicit def lazyRequestF[F[_]: Functor] = new Functor[LazyRequest[F, *]] {
    def map[A, B](fa: LazyRequest[F, A])(f: A => B): LazyRequest[F, B] = LazyRequest(
      fa.k.map { r =>
        r match {
          case a: FetchReqInfo[F, A] =>
            a.copy(
              getResultK = a.getResultK.map(_.map(f)),
              mapTo = a.mapTo.map(_.map(f))
            )
          case b: LiftedReqInfo[F, A] =>
            b.copy(getResultK = b.getResultK.map(_.map(f)))
          case c: PureReqInfo[F, A] =>
            c.copy(getResultK = c.getResultK.map(_.map(f)))
          case d: AllReqInfo[F, A] =>
            d.copy[F, B](getResultK = d.getResultK.map(_.map(f)))
        }
      }
    )
  }
  implicit def lazyRequestP[M[_]: Monad: Parallel] = new Parallel[LazyRequest[M, *]] {
    type F[x] = LazyBatchRequest[M, x]
    def sequential: LazyBatchRequest[M, *] ~> LazyRequest[M, *] =
      new FunctionK[LazyBatchRequest[M, *], LazyRequest[M, *]] {
        def apply[A](l: LazyBatchRequest[M, A]): LazyRequest[M, A] = l.unbatch
      }
    def parallel: LazyRequest[M, *] ~> LazyBatchRequest[M, *] =
      new FunctionK[LazyRequest[M, *], LazyBatchRequest[M, *]] {
        def apply[A](l: LazyRequest[M, A]): LazyBatchRequest[M, A] =
          l.toBatch
      }
    def applicative: Applicative[LazyBatchRequest[M, *]] = LazyBatchRequest.lazyBatchRequestA[M]
    def monad: Monad[LazyRequest[M, *]]                  = lazyRequestM[M]
  }
}

final case class LazyBatchRequest[F[_], A](
    k: Kleisli[F, FetchCache, LazyBatchRequest.BReqInfo[F, A]]
) {
  def combine[B](
      fb: LazyBatchRequest[F, B]
  )(implicit F: Apply[F]): LazyBatchRequest[F, (A, B)] = {
    LazyBatchRequest(
      k.map { aInfo => bInfo: LazyBatchRequest.BReqInfo[F, B] =>
        LazyBatchRequest.BReqInfo.combine(aInfo, bInfo)
      }.ap(fb.k)
    )
  }

  def runK(implicit
      F: FlatMap[F],
      P: Parallel[F]
  ): Kleisli[F, FetchCache, DedupedRequest[F, A]] = k.flatMapF { info =>
    info.run
  }

  def unbatch(implicit F: Monad[F], P: Parallel[F]): LazyRequest[F, A] = LazyRequest(
    k.flatMapF { info: LazyBatchRequest.BReqInfo[F, A] =>
      info.run.map { df =>
        LazyRequest.ReqInfo.pure[F, A](df.unsafeCache, df.last)
      }
    // LazyRequest.ReqInfo.pure[F, A]()
    }
  )
}

object LazyBatchRequest {
  private val emptyMap = Map.empty[Any, Any]

  // def single[F[_], I, A](fetch: Fetch[F, I, A])(i: I): LazyBatchRequest[F, Option[A]]

  final case class BReqInfo[F[_], A](
      prevCache: FetchCache,
      fetchAllReqs: Map[FetchId, Kleisli[F, FetchCache, DedupedRequest[F, Any]]],
      batchReqsPerFetch: Map[
        FetchId,
        (Set[Any], (Set[Any], FetchCache) => F[FetchCache])
      ],
      otherReqs: Map[Any, Kleisli[F, FetchCache, FetchCache]], // Accumulated lifted requests
      getResultK: Kleisli[
        F,
        FetchCache,
        DedupedRequest[F, A]
      ] // Defines how to get the actual, finalized result
  ) {
    def run(implicit F: FlatMap[F], P: Parallel[F]): F[DedupedRequest[F, A]] = {

      val liftedRequests: List[F[FetchCache]] = otherReqs.toList.map(_._2.apply(prevCache))
      val fetchAllRequests: List[F[FetchCache]] =
        fetchAllReqs.toList.map(_._2.apply(prevCache).map(_.unsafeCache))
      val requests: List[F[FetchCache]] =
        liftedRequests ++ fetchAllRequests ++ batchReqsPerFetch.toList.collect {
          case (fetchId, (reqSet, get)) if (fetchAllReqs.get(fetchId).isEmpty) =>
            get(reqSet, prevCache)
        }
      requests.parSequence
        .map(
          _.foldLeft(
            prevCache
          ) { case (c, next) =>
            c ++ next
          }
        )
        .flatMap(getResultK.run)
    }
  }

  object BReqInfo {
    def combine[F[_]: Apply, A, B](
        aInfo: BReqInfo[F, A],
        bInfo: BReqInfo[F, B]
    ): BReqInfo[F, (A, B)] = {
      val newK: Kleisli[F, FetchCache, DedupedRequest[F, B] => DedupedRequest[F, (A, B)]] =
        aInfo.getResultK.map { df =>
          val f: DedupedRequest[F, B] => DedupedRequest[F, (A, B)] =
            dfb => df.copy(last = df.last -> dfb.last)
          f
        }
      val combinedK: Kleisli[F, FetchCache, DedupedRequest[F, (A, B)]] = newK.ap(bInfo.getResultK)

      // Batch requests need to be manually combined
      val aKeys = aInfo.batchReqsPerFetch.keySet
      val updatedSets = aKeys.toList
        .map(id => id -> bInfo.batchReqsPerFetch.get(id))
        .collect { case (id, Some((valueSet, _))) =>
          aInfo.batchReqsPerFetch.get(id).map { case (oldSet, f) =>
            id -> ((valueSet ++ oldSet) -> f)
          }
        }
        .flattenOption
      val newSets               = bInfo.batchReqsPerFetch -- aKeys
      val combinedBatchRequests = aInfo.batchReqsPerFetch ++ updatedSets ++ newSets

      LazyBatchRequest.BReqInfo(
        aInfo.prevCache ++ bInfo.prevCache,
        aInfo.fetchAllReqs ++ bInfo.fetchAllReqs,
        combinedBatchRequests,
        aInfo.otherReqs ++ bInfo.otherReqs,
        combinedK
      )
    }
  }

  implicit def lazyBatchRequestA[F[_]: Monad]: CommutativeApplicative[
    LazyBatchRequest[F, *]
  ] =
    new CommutativeApplicative[LazyBatchRequest[F, *]] {
      def ap[A, B](
          ff: LazyBatchRequest[F, A => B]
      )(fa: LazyBatchRequest[F, A]): LazyBatchRequest[F, B] = {
        val combined = fa.combine(ff)
        LazyBatchRequest(
          k = combined.k.map(info =>
            info.copy(getResultK =
              info.getResultK.map(df => df.copy[F, B](last = df.last._2(df.last._1)))
            )
          )
        )
      }
      def pure[A](x: A): LazyBatchRequest[F, A] =
        LazyBatchRequest(
          Kleisli(c =>
            BReqInfo[F, A](
              c,
              Map.empty,
              Map.empty,
              Map.empty,
              Kleisli(c2 => DedupedRequest[F, A](c, x).pure[F])
            ).pure[F]
          )
        )
    }
}
