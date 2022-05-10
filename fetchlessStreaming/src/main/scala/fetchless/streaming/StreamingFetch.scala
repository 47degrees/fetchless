package fetchless.streaming

import cats.Functor
import cats.data.Kleisli
import cats.syntax.all._
import fetchless._
import fs2.Stream

trait StreamingFetch[F[_], I, A] extends Fetch[F, I, A] { self =>

  /**
   * An immediate request for a batch that is emitted as a stream. If your `Fetch` instance is
   * optimized for streaming, it may have better efficiency properties than if you request a batch
   * first.
   *
   * For example, using the default `DoobieFetch` as part of `fetchless-doobie` will enable you to
   * stream batches directly from your database, meaning you get more immediate results.
   */
  def streamingBatch(iSet: Set[I]): Stream[F, (I, Option[A])]

  /**
   * Same as `streamingBatch` but will only emit existing elements and skip over elements that do
   * not exist.
   */
  def streamingBatchFilterOption(iSet: Set[I]): Stream[F, (I, A)] = streamingBatch(iSet).collect {
    case (i, Some(a)) => i -> a
  }

}

object StreamingFetch {

  /**
   * Turns an existing `Fetch` into a `StreamingFetch` by adding a way to stream batches from your
   * data source. For the case where you do not have a normal means to stream batches from your data
   * source, you can use the `wrapExistingWithDefaults` constructor instead which is optimized for
   * turning regular batches into streams.
   */
  def wrapExisting[F[_], I, A](fetch: Fetch[F, I, A])(
      doStreamingBatch: Set[I] => Stream[F, (I, Option[A])]
  ) = new StreamingFetch[F, I, A] {
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

  }

  /**
   * Turns an existing `Fetch` into a `StreamingFetch` by adding a way to stream batches from your
   * data source. Differing from `wrapExisting`, this variant lets you use a stream that does not
   * have optional elements.
   *
   * For the case where you do not have a normal means to stream batches from your data source, you
   * can use the `wrapExistingWithDefaults` constructor instead which is optimized for turning
   * regular batches into streams.
   */
  def wrapExistingGuaranteed[F[_], I, A](fetch: Fetch[F, I, A])(
      doStreamingBatchGuaranteed: Set[I] => Stream[F, (I, A)]
  ) = new StreamingFetch[F, I, A] {
    val id: String = fetch.id

    def single(i: I): F[Option[A]] = fetch.single(i)

    def singleDedupe(i: I): F[DedupedRequest[F, Option[A]]] = fetch.singleDedupe(i)

    def singleDedupeCache(i: I)(cache: FetchCache): F[DedupedRequest[F, Option[A]]] =
      fetch.singleDedupeCache(i)(cache)

    def batch(iSet: Set[I]): F[Map[I, A]] = fetch.batch(iSet)

    def batchDedupe(iSet: Set[I]): F[DedupedRequest[F, Map[I, A]]] = fetch.batchDedupe(iSet)

    def batchDedupeCache(iSet: Set[I])(cache: FetchCache): F[DedupedRequest[F, Map[I, A]]] =
      fetch.batchDedupeCache(iSet)(cache)

    override def streamingBatchFilterOption(iSet: Set[I]): Stream[F, (I, A)] =
      doStreamingBatchGuaranteed(iSet)

    // Need to keep track of IDs that are not included in the streaming result, so we use `mapAccumulate`
    // to build up a set of found values and compare
    override def streamingBatch(iSet: Set[I]): Stream[F, (I, Option[A])] =
      streamingBatchFilterOption(iSet).zipWithNext
        .mapAccumulate(Set.empty[I]) {
          case (foundIs, ((i, a), Some(_))) =>
            (foundIs + i) -> Stream.emit(i -> Some(a))
          case (foundIs, ((i, a), None)) =>
            val newFound  = foundIs + i
            val missingIs = iSet.diff(newFound)
            newFound -> (Stream
              .emit(i -> Some(a)) ++ Stream.iterable(missingIs).map(i => i -> None))
        }
        .flatMap(_._2)

  }

  /**
   * Takes any existing `Fetch` and turns it into a `StreamingFetch` by providing default behavior
   * for the streaming methods. `streamingBatch` and its derivatives will just run a `batch` as
   * usual, and `streamingBatchFilterOption` is specially optimized for this case. In the event that
   * you actually have a means to stream results from your data source, try using `wrapExisting`
   * with a dedicated stream method provided instead.
   */
  def wrapExistingWithDefaults[F[_], I, A](fetch: Fetch[F, I, A]) = new StreamingFetch[F, I, A] {
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

  }
}
