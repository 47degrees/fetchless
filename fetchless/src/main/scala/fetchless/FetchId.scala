package fetchless

sealed trait FetchId extends Product with Serializable

object FetchId {

  /** A unique String-based ID, useful for `Fetch` instances. */
  final case class StringId(s: String) extends FetchId

  /**
   * A signifier that the related request is a lifted effect, and does not have a unique `FetchId`.
   */
  case object Lifted extends FetchId
}
