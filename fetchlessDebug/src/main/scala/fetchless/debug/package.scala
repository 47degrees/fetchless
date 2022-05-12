package fetchless

import cats.effect.{Clock, Concurrent}
import cats.data.Chain
import cats.syntax.all._

package object debug {
  def useDebug[F[_]: Concurrent: Clock, I, A, B](
      fetch: Fetch[F, I, A]
  )(f: Fetch[F, I, A] => F[B]): F[(B, Chain[DebugLog[I]])] = for {
    dFetch <- DebugFetch.wrap(fetch)
    b      <- f(dFetch)
    logs   <- dFetch.flushLogs
  } yield (b, logs)

  // TODO: remove this file once logging works
  def useDebug[F[_]: Concurrent: Clock, I, A, B](
      fetch: AllFetch[F, I, A]
  )(f: Fetch[F, I, A] => F[B]): F[(B, Chain[DebugLog[I]])] = for {
    dFetch <- DebugFetch.wrap(fetch)
    b      <- f(dFetch)
    logs   <- dFetch.flushLogs
  } yield (b, logs)
}
