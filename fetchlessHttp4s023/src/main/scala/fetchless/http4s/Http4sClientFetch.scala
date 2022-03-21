package fetchless.http4s

import fetchless.Fetch
import cats.{Monad, Parallel}
import org.http4s.{EntityDecoder, Request}
import org.http4s.client.Client

object Http4sClientFetch {
  def forEntitySequential[F[_]: Monad, I, A](id: String, client: Client[F])(
      f: I => Request[F]
  )(implicit ED: EntityDecoder[F, A]) = Fetch.singleSequenced[F, I, A](id) { i =>
    client.expectOption[A](f(i))
  }
  def forEntityParallel[F[_]: Monad: Parallel, I, A](id: String, client: Client[F])(
      f: I => Request[F]
  )(implicit ED: EntityDecoder[F, A]) =
    Fetch.singleParallel[F, I, A](id) { i =>
      client.expectOption[A](f(i))
    }
}
