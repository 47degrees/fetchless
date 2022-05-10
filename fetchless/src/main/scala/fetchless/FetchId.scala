package fetchless

sealed trait FetchId extends Product with Serializable

object FetchId {
  final case class StringId(s: String) extends FetchId
  case object Lifted                   extends FetchId
}
