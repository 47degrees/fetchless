package fetchless

import cats.data.Kleisli
import FetchCache.CacheMap
import cats.data.Chain
import scala.concurrent.duration.FiniteDuration

/**
 * A cache of values between requests.
 *
 * @param cacheMap
 *   A map of request values for every (request ID -> fetch ID) pairing. `Some` means there is a
 *   result, `None` means there is no result.
 * @param fetchAllAcc
 *   An accumulated set of fetch IDs that indicates every possible value was requested. Used by
 *   `AllFetch`.
 */
final case class FetchCache(
    cacheMap: CacheMap,
    fetchAllAcc: Set[FetchId.StringId],
    requestLog: Chain[FetchCache.RequestLogEntry]
) {
  // def +(kv: ((Any, FetchId), Option[Any])): FetchCache = copy(cacheMap = cacheMap + kv)
  def ++(c: FetchCache): FetchCache =
    FetchCache(cacheMap ++ c.cacheMap, fetchAllAcc ++ c.fetchAllAcc, requestLog ++ c.requestLog)
  // def ++(map: CacheMap): FetchCache      = ++(FetchCache(map, Set.empty))
  def addSingle(
      kv: ((Any, FetchId), Option[Any]),
      logEntry: FetchCache.RequestLogEntry.SingleRequest
  ): FetchCache =
    copy(cacheMap = cacheMap + kv, requestLog = requestLog :+ logEntry)
  def combine(c: FetchCache): FetchCache = ++(c)

  /** Tries to access a value from this cache */
  def get[F[_], I, A](fetch: Fetch[F, I, A])(i: I): FetchCache.GetValueResult[A] =
    cacheMap.get(i -> fetch.wrappedId) match {
      case Some(Some(value)) => FetchCache.GetValueResult.ValueExists(value.asInstanceOf[A])
      case Some(None)        => FetchCache.GetValueResult.ValueDoesNotExist()
      case None              => FetchCache.GetValueResult.ValueNotYetRequested()
    }

  /** Accesses every value for a given fetch in this cache */
  def getMap[F[_], I, A](fetch: Fetch[F, I, A]): Map[I, A] = cacheMap.collect[I, A] {
    case ((i, fid), Some(b)) if (fid == fetch.wrappedId) =>
      i.asInstanceOf[I] -> b.asInstanceOf[A]
  }

  /**
   * Like `getMap` but exclusively collects the IDs specified. The `GetValueResult` values provided
   * will say exactly which IDs were found, not found, or not requested yet.
   */
  def getMapForSet[F[_], I, A](
      fetch: Fetch[F, I, A]
  )(iSet: Set[I]): Map[I, FetchCache.GetValueResult[A]] = {
    val requested: Map[I, FetchCache.GetValueResult[A]] = cacheMap.collect {
      case ((i, fid), b) if (fid == fetch.wrappedId) =>
        b match {
          case Some(value) =>
            i.asInstanceOf[I] -> FetchCache.GetValueResult.ValueExists(value.asInstanceOf[A])
          case None => i.asInstanceOf[I] -> FetchCache.GetValueResult.ValueDoesNotExist()
        }
    }
    val notRequested = iSet
      .diff(requested.keySet)
      .toList
      .map(i => i -> FetchCache.GetValueResult.ValueNotYetRequested[A]())
    requested ++ notRequested
  }

  def getMapForSetOnlyExisting[F[_], I, A](
      fetch: Fetch[F, I, A]
  )(iSet: Set[I]): Map[I, A] =
    cacheMap.collect {
      case ((i, fid), Some(value)) if (fid == fetch.wrappedId) =>
        i.asInstanceOf[I] -> value.asInstanceOf[A]
    }

  def setFetchAllFor(id: FetchId.StringId): FetchCache = copy(fetchAllAcc = fetchAllAcc + id)

  def addLog(log: FetchCache.RequestLogEntry): FetchCache = copy(requestLog = requestLog :+ log)
}

object FetchCache {

  sealed trait ResultTime extends Product with Serializable

  object ResultTime {
    final case class Timed(duration: FiniteDuration) extends ResultTime
    case object Instantaneous                        extends ResultTime
    case object TimeNotRequested                     extends ResultTime
  }

  sealed trait GetValueResult[A] extends Product with Serializable

  object GetValueResult {
    final case class ValueExists[A](value: A)  extends GetValueResult[A]
    final case class ValueDoesNotExist[A]()    extends GetValueResult[A]
    final case class ValueNotYetRequested[A]() extends GetValueResult[A]
  }

  sealed trait RequestLogEntry extends Product with Serializable {
    val duration: ResultTime
  }

  object RequestLogEntry {
    final case class SingleRequest(
        fetchId: FetchId.StringId,
        requestId: Any,
        duration: ResultTime,
        succeeded: SingleRequestResult
    ) extends RequestLogEntry
    final case class BatchRequest(
        fetchId: FetchId.StringId,
        requestIdsFound: Set[Any],
        requestIdsMissing: Set[Any],
        duration: ResultTime
    ) extends RequestLogEntry
    final case class AllRequest(
        fetchId: FetchId.StringId,
        requestIdsFound: Set[Any],
        duration: ResultTime
    ) extends RequestLogEntry
    final case class LiftedRequest(contextId: Any, duration: ResultTime) extends RequestLogEntry
    final case class LiftedToCacheRequest(
        contextId: Any,
        requestIds: Map[FetchId.StringId, Any],
        duration: ResultTime
    ) extends RequestLogEntry
    final case class PureToCacheRequest(
        ids: Map[FetchId.StringId, Any],
        duration: ResultTime
    ) extends RequestLogEntry
  }

  sealed trait SingleRequestResult extends Product with Serializable

  object SingleRequestResult {
    case object ValueNotFound extends SingleRequestResult
    case object ValueFound    extends SingleRequestResult
  }

  /**
   * A map of request values for every (request ID -> fetch ID) pairing. `Some` means there is a
   * result, `None` means there is no result.
   */
  type CacheMap = Map[(Any, FetchId), Option[Any]]

  /** The default cache */
  val empty = FetchCache(Map.empty, Set.empty, Chain.empty)
}
