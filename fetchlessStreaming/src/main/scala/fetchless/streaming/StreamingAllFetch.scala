package fetchless.streaming

import cats.{Applicative, Functor}
import cats.data.Kleisli
import cats.syntax.all._
import fetchless._
import fs2.Stream

trait StreamingAllFetch[F[_], I, A] extends StreamingFetch[F, I, A] with AllFetch[F, I, A] {

  /** Streams all available elements from the current `Fetch` source. */
  def streamAll: Stream[F, (I, A)]
}

object StreamingAllFetch {

  /**
   * Turns an existing `AllFetch` into a `StreamingAllFetch` by allowing you to provide methods to
   * stream from your data source.
   *
   * If in your case you do not have a means to stream data naturally out of your data source, we
   * would recommend using `fromExistingAllFetchWithDefaults` instead as that is specially optimized
   * for that case.
   */
  def fromExistingAllFetch[F[_]: Functor, I, A](
      fetch: AllFetch[F, I, A]
  )(
      doStreamingBatch: Set[I] => Stream[F, (I, Option[A])]
  )(doStreamAll: Stream[F, (I, A)]): StreamingAllFetch[F, I, A] =
    new StreamingAllFetch[F, I, A] {
      val id: String = fetch.id

      def single(i: I): F[Option[A]] = fetch.single(i)

      def singleDedupe(i: I): F[DedupedRequest[F, Option[A]]] = fetch.singleDedupe(i)

      def singleDedupeCache(i: I)(cache: FetchCache): F[DedupedRequest[F, Option[A]]] =
        fetch.singleDedupeCache(i)(cache)

      def batch(iSet: Set[I]): F[Map[I, A]] = fetch.batch(iSet)

      def batchDedupe(iSet: Set[I]): F[DedupedRequest[F, Map[I, A]]] = fetch.batchDedupe(iSet)

      def batchDedupeCache(iSet: Set[I])(cache: FetchCache): F[DedupedRequest[F, Map[I, A]]] =
        fetch.batchDedupeCache(iSet)(cache)

      def streamingBatch(iSet: Set[I]): Stream[F, (I, Option[A])] =
        doStreamingBatch(iSet)

      override def streamingBatchFilterOption(iSet: Set[I]): Stream[F, (I, A)] =
        Stream.eval(batch(iSet)).flatMap(Stream.iterable)

      def batchAll: F[Map[I, A]] = fetch.batchAll

      def batchAllDedupe: F[DedupedRequest[F, Map[I, A]]] = fetch.batchAllDedupe

      def batchAllDedupeCache(cache: FetchCache): F[DedupedRequest[F, Map[I, A]]] =
        fetch.batchAllDedupeCache(cache)

      def batchAllLazy(implicit F: Applicative[F]): LazyRequest[F, Map[I, A]] = LazyRequest(
        Kleisli(c =>
          LazyRequest.ReqInfo
            .fetchAll[F, Map[I, A]](
              c,
              fetch.wrappedId,
              batchAllDedupeCache(c).asInstanceOf[F[DedupedRequest[F, Any]]],
              Kleisli(c2 => DedupedRequest[F, Map[I, A]](c2, c2.getMap(fetch)).pure[F])
            )
            .pure[F]
        )
      )

      def streamAll: Stream[F, (I, A)] = doStreamAll

    }

  /**
   * Turns an existing `AllFetch` into a `StreamingAllFetch` by providing default behavior for
   * stremaing methods.
   *
   * The default streaming methods may not be optimal, as they do not know anything about your data
   * source. If there is a way to actually stream data from your defined `Fetch` data source, use
   * `fromExistingAllFetch` instead and provide your own methods to stream data from your data
   * source.
   */
  def fromExistingAllFetchWithDefaults[F[_]: Functor, I, A](
      fetch: AllFetch[F, I, A]
  ): StreamingAllFetch[F, I, A] =
    new StreamingAllFetch[F, I, A] {
      val id: String = fetch.id

      def single(i: I): F[Option[A]] = fetch.single(i)

      def singleDedupe(i: I): F[DedupedRequest[F, Option[A]]] = fetch.singleDedupe(i)

      def singleDedupeCache(i: I)(cache: FetchCache): F[DedupedRequest[F, Option[A]]] =
        fetch.singleDedupeCache(i)(cache)

      def batch(iSet: Set[I]): F[Map[I, A]] = fetch.batch(iSet)

      def batchDedupe(iSet: Set[I]): F[DedupedRequest[F, Map[I, A]]] = fetch.batchDedupe(iSet)

      def batchDedupeCache(iSet: Set[I])(cache: FetchCache): F[DedupedRequest[F, Map[I, A]]] =
        fetch.batchDedupeCache(iSet)(cache)

      def streamingBatch(iSet: Set[I]): Stream[F, (I, Option[A])] =
        Stream.eval(batch(iSet)).flatMap(m => Stream.iterable(iSet).map(i => i -> m.get(i)))

      override def streamingBatchFilterOption(iSet: Set[I]): Stream[F, (I, A)] =
        Stream.eval(batch(iSet)).flatMap(Stream.iterable)

      def batchAll: F[Map[I, A]] = fetch.batchAll

      def batchAllDedupe: F[DedupedRequest[F, Map[I, A]]] = fetch.batchAllDedupe

      def batchAllDedupeCache(cache: FetchCache): F[DedupedRequest[F, Map[I, A]]] =
        fetch.batchAllDedupeCache(cache)

      def batchAllLazy(implicit F: Applicative[F]): LazyRequest[F, Map[I, A]] = LazyRequest(
        Kleisli { c =>
          LazyRequest.ReqInfo
            .fetchAll(
              c,
              fetch.wrappedId,
              batchAllDedupeCache(c).asInstanceOf[F[DedupedRequest[F, Any]]],
              Kleisli(c2 => DedupedRequest[F, Map[I, A]](c2, c2.getMap(fetch)).pure[F])
            )
            .pure[F]
        }
      )

      def streamAll: Stream[F, (I, A)] = Stream.eval(batchAll).flatMap(m => Stream.iterable(m))

    }

  /**
   * Turns an existing `StreamingFetch` into a `StreamingAllFetch` by allowing you to provide
   * methods for batching and streaming all possible
   */
  def fromExistingStreamingFetch[F[_]: Functor, I, A](fetch: StreamingFetch[F, I, A])(
      doBatchAll: F[Map[I, A]]
  )(doStreamAll: Stream[F, (I, A)]): StreamingAllFetch[F, I, A] =
    new StreamingAllFetch[F, I, A] {
      val id: String = fetch.id

      def single(i: I): F[Option[A]] = fetch.single(i)

      def singleDedupe(i: I): F[DedupedRequest[F, Option[A]]] = fetch.singleDedupe(i)

      def singleDedupeCache(i: I)(cache: FetchCache): F[DedupedRequest[F, Option[A]]] =
        fetch.singleDedupeCache(i)(cache)

      def batch(iSet: Set[I]): F[Map[I, A]] = fetch.batch(iSet)

      def batchDedupe(iSet: Set[I]): F[DedupedRequest[F, Map[I, A]]] = fetch.batchDedupe(iSet)

      def batchDedupeCache(iSet: Set[I])(cache: FetchCache): F[DedupedRequest[F, Map[I, A]]] =
        fetch.batchDedupeCache(iSet)(cache)

      def streamingBatch(iSet: Set[I]): Stream[F, (I, Option[A])] = fetch.streamingBatch(iSet)

      def batchAll: F[Map[I, A]] = doBatchAll

      def batchAllDedupe: F[DedupedRequest[F, Map[I, A]]] =
        batchAll.map(m =>
          DedupedRequest(
            m.toList
              .map { case (i, a) => (i -> fetch.id) -> a.some }
              .toMap
              .asInstanceOf[FetchCache],
            m
          )
        )

      def batchAllDedupeCache(cache: FetchCache): F[DedupedRequest[F, Map[I, A]]] = batchAll.map {
        m =>
          val cacheMap = cache ++ m.toList
            .map { case (i, a) => (i -> fetch.id) -> a.some }
            .toMap
            .asInstanceOf[FetchCache]
          DedupedRequest(
            cacheMap,
            m
          )
      }

      def batchAllLazy(implicit F: Applicative[F]): LazyRequest[F, Map[I, A]] = LazyRequest(
        Kleisli { c =>
          LazyRequest.ReqInfo
            .fetchAll(
              c,
              fetch.wrappedId,
              batchAllDedupeCache(c).asInstanceOf[F[DedupedRequest[F, Any]]],
              Kleisli(c2 => DedupedRequest[F, Map[I, A]](c2, c2.getMap(fetch)).pure[F])
            )
            .pure[F]
        }
      )

      def streamAll: Stream[F, (I, A)] = doStreamAll

    }
}
