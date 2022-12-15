package fetchless.doobie

import cats.Parallel
import cats.effect.MonadCancelThrow
import doobie.{ConnectionIO, Query, Query0}
import doobie.implicits._
import doobie.util.query
import doobie.util.transactor.Transactor
import fetchless.Fetch
import fetchless.streaming.{StreamingAllFetch, StreamingFetch}
import fs2.Stream

object DoobieFetch {

  /**
   * Creates a `Fetch` instance that makes one query at a time. For parallelism, you will need to
   * handle that at the transactor level by running each `ConnectionIO` produced.
   */
  def forQuery[I, A](fetchId: String)(q: Query[I, A]): Fetch[ConnectionIO, I, A] =
    Fetch.singleSequenced[ConnectionIO, I, A](fetchId) { i =>
      q.option(i)
    }

  /**
   * Creates a `Fetch` instance that makes one query at a time, automatically using the supplied
   * transactor.
   */
  def forQueryTransactSequenced[F[_]: MonadCancelThrow, I, A](fetchId: String, xa: Transactor[F])(
      q: Query[I, A]
  ): Fetch[F, I, A] =
    Fetch.singleSequenced[F, I, A](fetchId) { i =>
      q.option(i).transact(xa)
    }

  /**
   * Creates a `Fetch` instance that makes queries in parallel, automatically using the supplied
   * transactor.
   */
  def forQueryTransactParallel[F[_]: MonadCancelThrow: Parallel, I, A](
      fetchId: String,
      xa: Transactor[F]
  )(
      q: Query[I, A]
  ): Fetch[F, I, A] =
    Fetch.singleParallel[F, I, A](fetchId) { i =>
      q.option(i).transact(xa)
    }

  /**
   * Creates a `Fetch` instance that uses a special optimized batch query for batches. For
   * parallelism, you will need to handle that at the transactor level by running each
   * `ConnectionIO` produced.
   */
  def forBatchableQuery[I, A](
      fetchId: String
  )(single: Query[I, A])(batch: Set[I] => Query0[(I, A)]): StreamingFetch[ConnectionIO, I, A] = {
    val baseFetch =
      Fetch.batchable[ConnectionIO, I, A](fetchId)(i => single.option(i))(s => batch(s).toMap)
    StreamingFetch.wrapExistingGuaranteed(baseFetch)(iSet => batch(iSet).stream)
  }

  /**
   * Creates a `Fetch` instance that uses a special optimized batch query for batches. All queries
   * are transacted automatically using the provided transactor.
   */
  def forBatchableQueryTransact[F[_]: MonadCancelThrow, I, A](
      fetchId: String,
      xa: Transactor[F]
  )(single: Query[I, A])(batch: Set[I] => Query0[(I, A)]): Fetch[F, I, A] = {
    val baseFetch = Fetch.batchable[F, I, A](fetchId)(i => single.option(i).transact(xa))(s =>
      batch(s).toMap[I, A].transact(xa)
    )
    StreamingFetch.wrapExistingGuaranteed(baseFetch)(iSet => batch(iSet).stream.transact(xa))
  }

  /**
   * Creates a `Fetch` instance that uses a special optimized batch query for batches, as well as a
   * query to retrieve all available results. For parallelism, you will need to handle that at the
   * transactor level by running each `ConnectionIO` produced.
   */
  def forBatchableQueryAll[I, A](
      fetchId: String
  )(single: Query[I, A])(
      batch: Set[I] => Query0[(I, A)]
  )(batchAll: Query0[(I, A)]): StreamingFetch[ConnectionIO, I, A] = {
    val baseFetch =
      Fetch.batchable[ConnectionIO, I, A](fetchId)(i => single.option(i))(s => batch(s).toMap)
    val streamingFetch =
      StreamingFetch.wrapExistingGuaranteed(baseFetch)(iSet => batch(iSet).stream)
    StreamingAllFetch.fromExistingStreamingFetch(streamingFetch)(batchAll.toMap)(
      batchAll.stream
    )
  }

  /**
   * Creates a `Fetch` instance that uses a special optimized batch query for batches, as well as a
   * query to retrieve all available results. All queries are transacted automatically using the
   * provided transactor.
   */
  def forBatchableQueryTransactAll[F[_]: MonadCancelThrow, I, A](
      fetchId: String,
      xa: Transactor[F]
  )(
      single: Query[I, A]
  )(batch: Set[I] => Query0[(I, A)])(batchAll: Query0[(I, A)]): Fetch[F, I, A] = {
    val baseFetch = Fetch.batchable[F, I, A](fetchId)(i => single.option(i).transact(xa))(s =>
      batch(s).toMap[I, A].transact(xa)
    )
    val streamingFetch =
      StreamingFetch.wrapExistingGuaranteed(baseFetch)(iSet => batch(iSet).stream.transact(xa))
    StreamingAllFetch.fromExistingStreamingFetch(streamingFetch)(batchAll.toMap[I, A].transact(xa))(
      batchAll.stream.transact(xa)
    )
  }
}
