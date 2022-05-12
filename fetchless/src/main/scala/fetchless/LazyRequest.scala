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

  /**
   * Chains this request into another request that depends on the result of this one. If your next
   * request does not depend on the results of the current request, look into using `Parallel`
   * syntax instead such as `parTupled`, `parTraverse`, or `parSequence` to automatically
   * parallelize and batch requests where possible.
   */
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

  /** Chains this request into another effect, preserving the chain of deduplication. */
  def flatMapF[B](f: A => F[B])(id: Any)(implicit F: Monad[F]) = flatMap(
    f.andThen(LazyRequest.liftF(_)(id))
  )

  /** Runs this `LazyRequest`, returning the deduplicated results. */
  def run(implicit F: FlatMap[F]): F[DedupedRequest[F, A]] =
    k.run(FetchCache.empty)
      .flatMap { i =>
        i.run
      }

  /**
   * Converts this `LazyRequest` into a `LazyBatchRequest`. This is done internally by calling
   * `Parallel` syntax such as `parTupled`, `parTraverse`, or `parSequence` so you typically do not
   * have to call this yourself.
   */
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
                id -> getResultK.map(c =>
                  c.unsafeCache ++ FetchCache(
                    Map((id, FetchId.Lifted) -> Some(c.last)),
                    Set.empty,
                    Chain.one(
                      FetchCache.RequestLogEntry.LiftedRequest(
                        id,
                        FetchCache.ResultTime.TimeNotRequested
                      ) // TODO: wire in timer for this
                    )
                  )
                )
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

  /**
   * Represents "request info" for the current `LazyRequest`, including the cached results of
   * previous requests as well as information on how to perform the next request.
   *
   * When batching requests with `Parallel` syntax, the information provided here helps to normalize
   * and combine requests of varying types so that they can be batched together and deduplicated
   * effectively.
   */
  sealed trait ReqInfo[F[_], A] extends Product with Serializable {
    val prevCache: FetchCache
    def updateCache(extra: FetchCache): ReqInfo[F, A]
    def run(implicit F: Functor[F]): F[DedupedRequest[F, A]]
    def runCached(cache: FetchCache)(implicit F: Functor[F]): F[DedupedRequest[F, A]]
  }

  object ReqInfo {

    /**
     * Info describing a typical request. Used internally in case this request ever needs to be
     * batched.
     *
     * The value `reqId` is is a set of IDs if `isBatch` is true, and a single ID if false.
     *
     * The functions `doSingle` and `doBatch` are used internally depending on whether this request
     * is a single request, or a batch. It is important to know this, because we want to be able to
     * turn `LazyRequest` values into `LazyBatchRequest` values when using `Parallel` syntax. This
     * allows us to take the details and context of each request and combine them into batches
     * wherever possible.
     *
     * `getResultK` describes how to get the result from a cache (for example, if it has been
     * batched with other requests)
     *
     * `mapTo` is mainly used for keeping `map` operations performed after the fact intact, while
     * preserving the ability to deduplicate based on the original results.
     */
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

    /**
     * Describes an effect lifted into the context of a request. Needs a unique `reqId` for being
     * able to deduplicate lifted requests as well as properly parallelize them with batch requests.
     */
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
                val newCache = c ++ FetchCache(
                  Map((reqId -> FetchId.Lifted) -> Some(a)),
                  Set.empty,
                  Chain.one(
                    FetchCache.RequestLogEntry.LiftedRequest(
                      reqId,
                      FetchCache.ResultTime.TimeNotRequested
                    ) // TODO: wire in timer for this
                  )
                )
                DedupedRequest(newCache, a)
              }
            case Some(value) =>
              DedupedRequest(c, value.get).pure[F].asInstanceOf[F[DedupedRequest[F, A]]]
          }
        }
      )

    /**
     * Variant of `fetch` that assumes you will be getting every result.
     *
     * The supplied `fetchId` is used by the internal cache to keep track of which `Fetch` instances
     * have already been fully "pre-fetched".
     *
     * `runFetchAll` is the actual request to get all values, and `getResultK` is how it is
     * retrieved from a cache (if batched).
     */
    def fetchAll[F[_], A](
        inCache: FetchCache,
        fetchId: FetchId.StringId,
        runFetchAll: Kleisli[F, FetchCache, DedupedRequest[F, Any]],
        getResultK: Kleisli[F, FetchCache, DedupedRequest[F, A]]
    ): ReqInfo[F, A] =
      AllReqInfo(
        inCache,
        fetchId,
        runFetchAll,
        getResultK
      )

    /**
     * Information for a pure value lifted into a LazyRequest context.
     *
     * This is more optimized than using `lift` because
     */
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
        df.copy(unsafeCache =
          df.unsafeCache ++ FetchCache(
            Map((reqId, FetchId.Lifted) -> Some(df.last)),
            Set.empty,
            Chain.one(
              FetchCache.RequestLogEntry.LiftedRequest(
                reqId,
                FetchCache.ResultTime.TimeNotRequested
              ) // TODO: wire in timer for this
            )
          )
        )
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
   * chains and for-comprehensions. Requires a unique ID value so that, if this is included in a
   * batch of requests, it can also be deduplicated and the value properly retrieved.
   */
  def liftF[F[_]: Applicative, A](fa: F[A])(id: Any): LazyRequest[F, A] = LazyRequest(
    Kleisli(c => ReqInfo.lift(c, id, fa).pure[F])
  )

  /**
   * Lifts any pure value into a `LazyRequest` context, so you can use it in `flatMap` chains and
   * for-comprehensions.
   *
   * Unlike `liftF`, this does not require a unique ID as pure values do not need to be evaluated as
   * effects, and therefore not deduplicated.
   */
  def liftPure[F[_]: Applicative, A](a: A): LazyRequest[F, A] = LazyRequest(
    Kleisli(c => LazyRequest.ReqInfo.pure[F, A](c, a).pure[F])
  )

  /**
   * `cats.Monad` instance for `LazyRequest` so you can `flatMap` and lift `pure` values into a
   * `LazyRequest` context.
   */
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

  /** `cats.Functor` instance for `LazyRequest`, so you can `map` the output results. */
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

  /**
   * `cats.Parallel` instance for `LazyRequest`. Enables automatic batching and parallelism with
   * parallel syntax such as `parTupled`, `parTraverse`, `parSequence` syntax.
   */
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

/**
 * The applicative counterpart of `LazyRequest` that is designed for combining multiple independent
 * requests together.
 */
final case class LazyBatchRequest[F[_], A](
    k: Kleisli[F, FetchCache, LazyBatchRequest.BReqInfo[F, A]]
) {

  /**
   * Combines two `LazyBatchRequest` instances together. Same as
   * `Applicative[LazyBatchRequest].product`.
   */
  def combine[B](
      fb: LazyBatchRequest[F, B]
  )(implicit F: Apply[F]): LazyBatchRequest[F, (A, B)] = {
    LazyBatchRequest(
      k.map { aInfo => bInfo: LazyBatchRequest.BReqInfo[F, B] =>
        LazyBatchRequest.BReqInfo.combine(aInfo, bInfo)
      }.ap(fb.k)
    )
  }

  /**
   * Converts this `LazyBatchRequest` into a `LazyRequest`. Used internally by the `LazyRequest`
   * `Parallel` instance, and is not needed to call manually.
   */
  def unbatch(implicit F: Monad[F], P: Parallel[F]): LazyRequest[F, A] = LazyRequest(
    k.flatMapF { info: LazyBatchRequest.BReqInfo[F, A] =>
      info.run.map { df =>
        LazyRequest.ReqInfo.pure[F, A](df.unsafeCache, df.last)
      }
    }
  )
}

object LazyBatchRequest {

  /**
   * Similar to `LazyRequest.ReqInfo`, but specifically tailored to batch requests. Allows for
   * combining multiple kinds of requests and efficiently organizing them so that when they are run,
   * they are deduplicated properly and ran in parallel if supported by your base effect type.
   *
   * @param prevCache
   *   All previously batched requests
   * @param fetchAllReqs
   *   `FetchAll` requests that have yet to be made. All IDs in this map are also in the above
   *   cache.
   * @param batchReqsPerFetch
   *   Map of batch requests per-`FetchId`. Includes a set of IDs as well as a function to apply
   *   them to.
   * @param otherReqs
   *   Map of accumulated lifted requests by-ID.
   * @param getResultK
   *   A function that retrieves the final value from the accumulated `FetchCache` result of running
   *   all requests.
   */
  final case class BReqInfo[F[_], A](
      prevCache: FetchCache,
      fetchAllReqs: Map[FetchId, Kleisli[F, FetchCache, DedupedRequest[F, Any]]],
      batchReqsPerFetch: Map[
        FetchId,
        (Set[Any], (Set[Any], FetchCache) => F[FetchCache])
      ],
      otherReqs: Map[Any, Kleisli[F, FetchCache, FetchCache]],
      getResultK: Kleisli[
        F,
        FetchCache,
        DedupedRequest[F, A]
      ]
  ) {

    /** Runs the request represented by this `BReqInfo` */
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

    /**
     * Combines two batch request info instances into a product, and combines all accumulated
     * request data.
     */
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
