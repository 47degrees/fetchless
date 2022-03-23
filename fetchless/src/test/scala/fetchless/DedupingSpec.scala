package fetchless

import syntax._

import cats.syntax.all._
import cats._
import munit.FunSuite

class DedupingSpec extends FunSuite {

  val exampleKey = ("1" -> "str")

  test("DedupedRequest absorb") {
    val dedupeA = DedupedRequest(Map(exampleKey -> 1.some), none[Int])
    val dedupeB = DedupedRequest(Map(exampleKey -> none[Int]), none[Int])

    assert(dedupeA.absorb(dedupeB).unsafeCache == Map(exampleKey -> none[Int]))
  }

  test("DedupedRequest flatMap") {
    val key2    = ("2" -> "str")
    val dedupeA = DedupedRequest[Id, Option[Int]](Map(exampleKey -> 1.some), 4.some)
    val dedupeB = DedupedRequest[Id, Option[Int]](Map(key2 -> 2.some), 5.some)

    val result = dedupeA.flatMap {
      case None    => dedupeB
      case Some(i) => dedupeB.copy(last = i.some)
    }

    val expected = DedupedRequest[Id, Option[Int]](
      Map(
        exampleKey -> 1.some,
        key2       -> 2.some
      ),
      4.some
    )

    assertEquals(result, expected)
  }

  test("Dedupe across multiple fetches") {
    var timesIntsFetched = 0

    implicit val intFetch = Fetch.singleSequenced[Id, Int, Int]("intFetch") { i =>
      timesIntsFetched += 1
      Some(i)
    }

    implicit val boolFetch = Fetch.batchable[Id, Boolean, Boolean](
      "boolFetch"
    )(i => Some(i))(iSet => iSet.toList.map(i => (i, i)).toMap)

    val result = intFetch
      .singleDedupe(5)
      .alsoFetch[Int, Int](6)
      .alsoFetch[Int, Int](5)
      .alsoFetch[Boolean, Boolean](true)
      .alsoFetch[Int, Int](6)

    assert(
      result.unsafeCache == Map(
        (5    -> "intFetch")  -> 5.some,
        (6    -> "intFetch")  -> 6.some,
        (true -> "boolFetch") -> true.some
      )
    )

    assertEquals(result.last, 6.some)
    assertEquals(timesIntsFetched, 2)
  }
}
