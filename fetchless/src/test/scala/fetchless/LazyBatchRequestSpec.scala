package fetchless

import munit.FunSuite
import cats.Id
import cats.syntax.all._

class LazyBatchRequestSpec extends FunSuite {

  implicit val intFetch = Fetch.singleSequenced[Id, Int, Int]("intFetch") { i =>
    Some(i)
  }

  implicit val boolFetch = Fetch.singleSequenced[Id, Boolean, Boolean]("boolFetch") { i =>
    Some(i)
  }

  test("LazyBatchRequest lets you batch multiple times") {
    val one   = LazyBatchRequest.single(intFetch)(1)
    val two   = LazyBatchRequest.single(intFetch)(2)
    val three = LazyBatchRequest.single(intFetch)(3)

    val result = (one, two, three).tupled.run

    assertEquals(result.last, (Some(1), Some(2), Some(3)))
  }

  test("LazyBatchRequest lets you combine multiple explicit batches from multiple sources") {
    val fewInts     = LazyBatchRequest.many(intFetch)(Set(1, 2, 3))
    val two         = LazyBatchRequest.single(intFetch)(2)
    val coupleBools = LazyBatchRequest.many(boolFetch)(Set(true, false))

    val result = (fewInts, two, coupleBools).tupled.run

    assertEquals(
      result.last,
      (Map(1 -> 1, 2 -> 2, 3 -> 3), Some(2), Map(true -> true, false -> false))
    )
  }

}
