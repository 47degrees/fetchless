package fetchless.http4s

import fetchless.Fetch
import cats.{Monad, Parallel}
import org.http4s.{EntityDecoder, Request}
import org.http4s.client.Client
import cats.MonadThrow
import cats.syntax.all._

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
  )(implicit ED: EntityDecoder[F, A]) = Fetch.singleSequenced[F, I, A](id) { i =>
    client.expectOption[A](f(i))
  }

  /**
   * Creates a `Fetch` instance that makes parallel batch queries using `client.expectOption`. Any
   * HTTP 404/410 responses are resolved as `None` while other HTTP errors will be raised as errors.
   */
  def forEntityParallel[F[_]: Monad: Parallel, I, A](id: String, client: Client[F])(
      f: I => Request[F]
  )(implicit ED: EntityDecoder[F, A]) =
    Fetch.singleParallel[F, I, A](id) { i =>
      client.expectOption[A](f(i))
    }
}
