package fetchless

import syntax._

import cats.syntax.all._
import cats._
import munit.FunSuite

class DedupingSpec extends FunSuite {

  val exampleKey = ("1" -> "str")

  test("DedupedFetch absorb") {
    val dedupeA = DedupedFetch(Map(exampleKey -> 1.some), none[Int])
    val dedupeB = DedupedFetch(Map(exampleKey -> none[Int]), none[Int])

    assert(dedupeA.absorb(dedupeB).cache == Map(exampleKey -> none[Int]))
  }

  test("DedupedFetch flatMap") {
    val key2    = ("2" -> "str")
    val dedupeA = DedupedFetch[Id, Option[Int]](Map(exampleKey -> 1.some), 4.some)
    val dedupeB = DedupedFetch[Id, Option[Int]](Map(key2 -> 2.some), 5.some)

    val result = dedupeA.flatMap {
      case None    => dedupeB
      case Some(i) => dedupeB.copy(last = i.some)
    }

    val expected = DedupedFetch(
      Map(
        exampleKey -> 1.some,
        key2       -> 2.some
      ),
      4.some
    )
  }

  test("Dedupe across multiple fetches") {
    var timesIntsFetched = 0

    implicit val intFetch = Fetch.singleSequenced[Id, Int, Int]("intFetch") { i =>
      timesIntsFetched += 1
      Some(i)
    }

    implicit val boolFetch = new Fetch[Id, Boolean, Boolean] {
      val id: String = "boolFetch"

      def single(i: Boolean): Id[Option[Boolean]] = Some(i)

      def batch(iSet: Set[Boolean]): Id[Map[Boolean, Boolean]] = iSet.toList.map(i => (i, i)).toMap

    }

    val result = intFetch
      .singleDedupe(5)
      .alsoFetch[Int, Int](6)
      .alsoFetch[Int, Int](5)
      .alsoFetch[Boolean, Boolean](true)
      .alsoFetch[Int, Int](6)

    assert(
      result.cache == Map(
        (5    -> "intFetch")  -> 5.some,
        (6    -> "intFetch")  -> 6.some,
        (true -> "boolFetch") -> true.some
      )
    )

    assertEquals(result.last, 6.some)
  }
}
