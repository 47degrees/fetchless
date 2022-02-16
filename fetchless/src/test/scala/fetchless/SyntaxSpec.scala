package fetchless

import syntax._

import cats.syntax.all._
import cats._
import munit.FunSuite

class SyntaxSpec extends FunSuite {

  implicit val dummyFetch =
    Fetch.batchable[Id, Int, Int]("dummyFetch")(Some(_))(is => is.toList.map(i => (i * 2, i)).toMap)

  test("List batching") {
    val result = List(1, 2, 3).fetchAll(dummyFetch)
    assertEquals(
      result,
      Map(
        2 -> 1,
        4 -> 2,
        6 -> 3
      )
    )
  }

  test("Single syntax") {
    val result = 1.fetch(dummyFetch)
    assertEquals(result, Some(1))
  }

  test("Effectful syntax") {
    val result = 1.pure[Id].fetch[Int]
    assertEquals(result, Some(1))
  }

  test("Tuple batching") {
    val resultBatchAll = (1, 2, 3).fetchAll[Id, Int]
    assertEquals(
      resultBatchAll,
      Map(
        2 -> 1,
        4 -> 2,
        6 -> 3
      )
    )

    val resultTupled = (1, 2, 3, 4, 5, 6).fetchTupled[Id, Int]
    assertEquals(resultTupled, (None, Some(1), None, Some(2), None, Some(3)))
  }
}
