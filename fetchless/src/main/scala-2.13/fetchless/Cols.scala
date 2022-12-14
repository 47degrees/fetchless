package fetchless

object Cols {

  def map[A, B, C, D](map: Map[A, B])(f: ((A, B)) => (C, D)): Map[C, D] =
    map.view.map(f).toMap

  def mapValues[A, B, C](map: Map[A, B])(f: B => (C)): Map[A, C] =
    map.view.mapValues(f).toMap

  def collect[A, B, C, D](map: Map[A, B])(f: PartialFunction[(A, B), (C, D)]): Map[C, D] =
    map.collect(f)

}
