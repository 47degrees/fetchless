package fetchless

object Cols {

  def map[A, B, C, D](map: Map[A, B])(f: ((A, B)) => (C, D)): Map[C, D] =
    map.map(f)

  def mapValues[A, B, C](map: Map[A, B])(f: B => (C)): Map[A, C] =
    map.mapValues(f).toMap

  def collect[A, B, C, D](map: Map[A, B])(f: PartialFunction[(A, B), (C, D)]): Map[C, D] =
    map.collect(f)

}
