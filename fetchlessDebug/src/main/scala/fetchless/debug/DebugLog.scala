package fetchless.debug

import scala.concurrent.duration.FiniteDuration

final case class DebugLog[I](
    fetchId: String,
    fetchTime: FiniteDuration,
    fetchType: DebugLog.FetchType[I]
)

object DebugLog {
  sealed trait FetchType[I] extends Product with Serializable

  object FetchType {
    final case class Fetch[I](id: I)                  extends FetchType[I]
    final case class FetchDedupe[I](id: I)            extends FetchType[I]
    final case class FetchLazy[I](id: I)              extends FetchType[I]
    final case class FetchBatch[I](ids: Set[I])       extends FetchType[I]
    final case class FetchBatchDedupe[I](ids: Set[I]) extends FetchType[I]
    final case class FetchBatchLazy[I](ids: Set[I])   extends FetchType[I]
  }
}
