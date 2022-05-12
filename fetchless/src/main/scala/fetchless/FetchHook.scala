package fetchless

import scala.concurrent.duration.FiniteDuration

sealed trait FetchHook[F[_]] {}

object FetchHook {
  final case class HookContext[I, C](fetchId: FetchId.StringId, additionalContext: C)
}
