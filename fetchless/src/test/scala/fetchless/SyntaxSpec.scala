package fetchless

import syntax._

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import cats.syntax.all._
import cats._

class SyntaxSpec extends AnyFunSuite with Matchers {

  implicit val dummyFetch = new Fetch[Id, Int, Int] {
    def single(i: Int): Id[Option[Int]] = Some(i)

    def batch(iSet: Set[Int]): Id[Map[Int, Int]] = iSet.toList.map(i => (i * 2, i)).toMap

  }

  test("List batching") {
    val result = List(1, 2, 3).batchAll(dummyFetch)
    result shouldEqual Map(
      2 -> 1,
      4 -> 2,
      6 -> 3
    )
  }

  test("Single syntax") {
    val result = 1.fetch(dummyFetch)
    result shouldEqual Some(1)
  }

  test("Effectful syntax") {
    val result = 1.pure[Id].fetch[Int]
    result shouldEqual Some(1)
  }

  test("Tuple batching") {
    val resultBatchAll = (1, 2, 3).batchAll[Id, Int]
    resultBatchAll shouldEqual Map(
      2 -> 1,
      4 -> 2,
      6 -> 3
    )

    val resultTupled = (1, 2, 3, 4, 5, 6).batchTupled[Id, Int]
    resultTupled shouldEqual (None, Some(1), None, Some(2), None, Some(3))
  }
}
