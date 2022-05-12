package fetchless

import cats.Functor
import cats.effect.Clock
import cats.syntax.all._

trait FetchTimer[F[_]] {
  def time[A](f: F[A]): F[(FetchCache.ResultTime, A)]
}

object FetchTimer {
  def noop[F[_]: Functor] = new FetchTimer[F] {
    def time[A](f: F[A]): F[(FetchCache.ResultTime, A)] =
      f.map(FetchCache.ResultTime.TimeNotRequested -> _)
  }

  def clock[F[_]: Clock: Functor] = new FetchTimer[F] {
    def time[A](f: F[A]): F[(FetchCache.ResultTime, A)] =
      Clock[F].timed(f).map { case (t, a) => FetchCache.ResultTime.Timed(t) -> a }
  }
}
