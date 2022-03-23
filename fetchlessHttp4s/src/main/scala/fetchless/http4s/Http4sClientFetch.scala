package fetchless.http4s

import fetchless.Fetch
import cats.{Monad, Parallel}
import org.http4s.{EntityDecoder, Request}
import org.http4s.client.Client
import cats.MonadThrow
import cats.syntax.all._
import fetchless.streaming.StreamingFetch
import fetchless.AllFetch

/** Constructors for `Fetch` instances based on http4s HTTP clients. */
object Http4sClientFetch {

  /**
   * Creates a `Fetch` instance that makes sequential batch queries using `client.expectOption`. Any
   * HTTP 404/410 responses are resolved as `None` while other HTTP errors will be raised as errors.
   */
  def forEntitySequential[F[_]: MonadThrow, I, A](
      id: String,
      client: Client[F]
  )(
      f: I => Request[F]
  )(implicit ED: EntityDecoder[F, A]): Fetch[F, I, A] = {
    Fetch.singleSequenced[F, I, A](id) { i =>
      client.expectOption[A](f(i))
    }
  }

  /**
   * Creates a `Fetch` instance that makes parallel batch queries using `client.expectOption`. Any
   * HTTP 404/410 responses are resolved as `None` while other HTTP errors will be raised as errors.
   */
  def forEntityParallel[F[_]: Monad: Parallel, I, A](id: String, client: Client[F])(
      f: I => Request[F]
  )(implicit ED: EntityDecoder[F, A]): Fetch[F, I, A] =
    Fetch.singleParallel[F, I, A](id) { i =>
      client.expectOption[A](f(i))
    }

  final class BatchableHttp4sClientFetchPartiallyApplied[B, I, A](
      bToMap: B => Map[I, A]
  ) {

    /** Creates a `Fetch` instance that can batch records by ID only. */
    def fetch[F[_]: Monad](id: String, client: Client[F])(single: I => Request[F])(
        batch: Set[I] => Request[F]
    )(implicit eda: EntityDecoder[F, A], edb: EntityDecoder[F, B]): Fetch[F, I, A] =
      Fetch.batchable[F, I, A](id)(i => client.expectOption[A](single(i)))(iSet =>
        client.expectOption[B](batch(iSet)).map(_.map(bToMap).getOrElse(Map.empty))
      )

    /**
     * Creates a `Fetch` instance that can batch records by ID as well as batch all records at once
     * without specifying IDs.
     */
    def allFetch[F[_]: Monad](id: String, client: Client[F])(
        single: I => Request[F]
    )(batch: Set[I] => Request[F])(
        all: Request[F]
    )(implicit eda: EntityDecoder[F, A], edb: EntityDecoder[F, B]): AllFetch[F, I, A] = {
      val baseFetch = Fetch.batchable[F, I, A](id)(i => client.expectOption[A](single(i)))(iSet =>
        client.expectOption[B](batch(iSet)).map(_.map(bToMap).getOrElse(Map.empty))
      )
      AllFetch.fromExisting(baseFetch)(
        client.expectOption[B](all).map(_.map(bToMap).getOrElse(Map.empty))
      )
    }
  }

  /**
   * Partially-applies a set of constructors for `Fetch` instances that support batching via http4s.
   * The partial application works by first asking you for a function to convert from your batch
   * result type to a `Map[I, A]` before asking for the other parameters, as they are dependent on
   * the type of `Fetch` you will create.
   *
   * Currently there are two sub-methods: `fetch` that creates a normal instance of `Fetch` and
   * `allFetch` that creates an `AllFetch` that is capable of batching all records at once.
   */
  def forBatchableEntity[B, I, A](bToMap: B => Map[I, A]) =
    new BatchableHttp4sClientFetchPartiallyApplied(bToMap)

}
