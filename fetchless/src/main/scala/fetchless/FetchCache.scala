package fetchless

import cats.data.Kleisli
import FetchCache.CacheMap

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
final case class FetchCache(cacheMap: CacheMap, fetchAllAcc: Set[FetchId.StringId]) {
  def +(kv: ((Any, FetchId), Option[Any])): FetchCache = copy(cacheMap = cacheMap + kv)
  def ++(c: FetchCache): FetchCache =
    FetchCache(cacheMap ++ c.cacheMap, fetchAllAcc ++ c.fetchAllAcc)
  def ++(map: CacheMap): FetchCache      = ++(FetchCache(map, Set.empty))
  def combine(c: FetchCache): FetchCache = ++(c)

  /** Tries to access a value from this cache */
  def get[F[_], I, A](fetch: Fetch[F, I, A])(i: I): Option[A] =
    cacheMap.get(i -> fetch.wrappedId).flatten.asInstanceOf[Option[A]]

  /** Accesses every value for a given fetch in this cache */
  def getMap[F[_], I, A](fetch: Fetch[F, I, A]): Map[I, A] = cacheMap.collect[I, A] {
    case ((i, fid), Some(b)) if (fid == fetch.wrappedId) =>
      i.asInstanceOf[I] -> b.asInstanceOf[A]
  }

  /** Like `getMap` but exclusively collects the IDs specified. */
  def getMapForSet[F[_], I, A](fetch: Fetch[F, I, A])(iSet: Set[I]): Map[I, A] =
    cacheMap.collect[I, A] {
      case ((i, fid), Some(b))
          if (fid == fetch.wrappedId && iSet.asInstanceOf[Set[Any]].contains(i)) =>
        i.asInstanceOf[I] -> b.asInstanceOf[A]
    }

  def setFetchAllFor(id: FetchId.StringId): FetchCache = copy(fetchAllAcc = fetchAllAcc + id)
}

object FetchCache {

  /**
   * A map of request values for every (request ID -> fetch ID) pairing. `Some` means there is a
   * result, `None` means there is no result.
   */
  type CacheMap = Map[(Any, FetchId), Option[Any]]

  /** The default cache */
  val empty = FetchCache(Map.empty, Set.empty)
}
