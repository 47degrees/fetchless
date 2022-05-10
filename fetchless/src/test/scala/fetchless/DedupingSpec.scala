package fetchless

import syntax._

import cats.syntax.all._
import cats._
import munit.FunSuite

class DedupingSpec extends FunSuite {

  val exampleKey = ("1" -> FetchId.StringId("str"))

  test("DedupedRequest absorb") {
    val dedupeA = DedupedRequest(FetchCache(Map(exampleKey -> 1.some), Set.empty), none[Int])
    val dedupeB = DedupedRequest(FetchCache(Map(exampleKey -> none[Int]), Set.empty), none[Int])

    assertEquals(
      dedupeA.absorb(dedupeB).unsafeCache.cacheMap,
      Map(exampleKey -> none[Int]).asInstanceOf[FetchCache.CacheMap]
    )
  }

  test("DedupedRequest flatMap") {
    val key2 = ("2" -> FetchId.StringId("str"))
    val dedupeA =
      DedupedRequest[Id, Option[Int]](FetchCache(Map(exampleKey -> 1.some), Set.empty), 4.some)
    val dedupeB =
      DedupedRequest[Id, Option[Int]](FetchCache(Map(key2 -> 2.some), Set.empty), 5.some)

    val result = dedupeA.flatMap {
      case None    => dedupeB
      case Some(i) => dedupeB.copy(last = i.some)
    }

    val expected = DedupedRequest[Id, Option[Int]](
      FetchCache(
        Map(
          exampleKey -> 1.some,
          key2       -> 2.some
        ),
        Set.empty
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

    assertEquals(
      result.unsafeCache.cacheMap,
      Map(
        (5    -> FetchId.StringId("intFetch"))  -> 5.some,
        (6    -> FetchId.StringId("intFetch"))  -> 6.some,
        (true -> FetchId.StringId("boolFetch")) -> true.some
      ).asInstanceOf[FetchCache.CacheMap]
    )

    assertEquals(result.last, 6.some)
    assertEquals(timesIntsFetched, 2)
  }
}
