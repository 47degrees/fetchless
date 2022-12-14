package fetchless

import syntax._

import cats._
import cats.syntax.all._
import cats.effect.{IO, Ref}
import munit.CatsEffectSuite

class LazyRequestSpec extends CatsEffectSuite {

  test("LazyRequest allows for non-linear deduping") {
    def intFetch(countRef: Ref[IO, Int], logRef: Ref[IO, List[Int]]): Fetch[IO, Int, Int] =
      Fetch.singleSequenced[IO, Int, Int]("intFetch")(i =>
        countRef.update(_ + 1) >> logRef.update(_ :+ i) >> IO(i.some)
      )

    val firstProgram = (IO.ref(0), IO.ref(List.empty[Int])).tupled.flatMap {
      case (countRef, logRef) =>
        implicit val fetch: Fetch[IO, Int, Int] = intFetch(countRef, logRef)

        val fetchProgram = fetch.singleLazy(1) >> fetch.singleLazy(2) >> fetch.singleLazy(2)
        val testProgram  = fetchProgram.run >> (countRef.get, logRef.get).tupled

        testProgram.assertEquals(2 -> List(1, 2)) // We never fetch 2 more than once
    }

    val secondProgram = (IO.ref(0), IO.ref(List.empty[Int])).tupled.flatMap {
      case (countRef, logRef) =>
        implicit val fetch: Fetch[IO, Int, Int] = intFetch(countRef, logRef)

        val fetchProgram = (fetch.singleLazy(2) >> fetch
          .singleLazy(1)) >> (fetch.singleLazy(1) >> fetch.singleLazy(2) >> fetch
          .singleLazy(2))

        val testProgram = fetchProgram.run >> (countRef.get, logRef.get).tupled
        testProgram.assertEquals(2 -> List(2, 1)) // We pre-fetch 2 before 1
    }

    firstProgram >> secondProgram
  }

  test("Has a parallel instance with LazyBatchRequest") {
    var timesBatchCalled = 0

    implicit val intFetch: Fetch[Id, Int, Int] = Fetch.batchable[Id, Int, Int]("intFetch") { i =>
      Some(i)
    } { is =>
      timesBatchCalled += 1
      is.toList.map(i => i -> i).toMap
    }

    val requests = List(
      intFetch.singleLazy(1),
      intFetch.singleLazy(2),
      intFetch.singleLazy(3)
    )

    val resultsLinear = (intFetch.singleLazy(5) >> requests.sequence).run

    val resultsPar = (intFetch.singleLazy(5) >> requests.parSequence).run

    assertEquals(resultsLinear, resultsPar)

    assertEquals(
      resultsPar,
      DedupedRequest[Id, List[Option[Int]]](
        FetchCache(
          Map(
            (5, intFetch.wrappedId) -> Some(5),
            (1, intFetch.wrappedId) -> Some(1),
            (2, intFetch.wrappedId) -> Some(2),
            (3, intFetch.wrappedId) -> Some(3)
          ),
          Set.empty
        ),
        List(Some(1), Some(2), Some(3))
      )
    )
    assertEquals(timesBatchCalled, 1)
  }

  test("Runs requests across multiple sources") {
    implicit val intFetch: Fetch[Id, Int, Int] = Fetch.singleSequenced[Id, Int, Int]("intFetch") {
      i =>
        Some(i)
    }

    implicit val boolFetch: Fetch[Id, Boolean, Boolean] =
      Fetch.singleSequenced[Id, Boolean, Boolean]("boolFetch") { i =>
        Some(i)
      }

    val fewInts     = intFetch.batchLazy(Set(1, 2, 3))
    val two         = LazyRequest.liftF[Id, String]("Hello world!")("two")
    val coupleBools = boolFetch.batchLazy(Set(true, false))

    val resultLinear = (fewInts, two, coupleBools).tupled.run
    val resultPar    = (fewInts, two, coupleBools).parTupled.run
    assertEquals(
      resultPar.last,
      (
        Map(1 -> 1, 2 -> 2, 3 -> 3),
        "Hello world!",
        Map(true -> true, false -> false)
      )
    )

    assertEquals(
      resultPar.unsafeCache,
      FetchCache(
        Map(
          (1, FetchId.StringId("intFetch"))      -> Some(1),
          (2, FetchId.StringId("intFetch"))      -> Some(2),
          (3, FetchId.StringId("intFetch"))      -> Some(3),
          (true, FetchId.StringId("boolFetch"))  -> Some(true),
          (false, FetchId.StringId("boolFetch")) -> Some(false),
          ("two", FetchId.Lifted)                -> Some("Hello world!")
        ),
        Set.empty
      )
    )

    assertEquals(resultLinear, resultPar)
  }

  test("Dedupes lifted requests (linear)") {
    var counter = 0

    val fetchOne = LazyRequest.liftF(IO {
      counter = counter + 1
      1
    })("fetchOne")

    val results = List(fetchOne, fetchOne, fetchOne).sequence.run

    val checkResult = results.assertEquals(
      DedupedRequest[IO, List[Int]](
        FetchCache(Map(("fetchOne", FetchId.Lifted) -> Some(1)), Set.empty),
        List(1, 1, 1)
      )
    )

    val checkCounter = IO(assertEquals(counter, 1))

    checkResult >> checkCounter
  }

  test("Dedupes lifted requests (parallel)") {
    var counter = 0

    val fetchOne = LazyRequest.liftF(IO {
      counter = counter + 1
      1
    })("fetchOne")

    val results = List(fetchOne, fetchOne, fetchOne).parSequence.run

    val checkResult = results.assertEquals(
      DedupedRequest[IO, List[Int]](
        FetchCache(Map(("fetchOne", FetchId.Lifted) -> Some(1)), Set.empty),
        List(1, 1, 1)
      )
    )

    val checkCounter = IO(assertEquals(counter, 1))

    checkResult >> checkCounter
  }

  test("Dedupes AllFetch requests (linear)") {
    var counter = 0

    val boolFetch = AllFetch.fromExisting(Fetch.echo[IO, Boolean]("boolFetch"))(IO {
      counter = counter + 1
      Map(true -> true, false -> false)
    })

    val results =
      List(boolFetch.batchAllLazy, boolFetch.batchAllLazy, boolFetch.batchAllLazy).sequence.run

    val expectedMap = Map(true -> true, false -> false)

    val checkResult = results.assertEquals(
      DedupedRequest[IO, List[Map[Boolean, Boolean]]](
        FetchCache(
          Map(
            (true, FetchId.StringId("boolFetch")) -> Some(true),
            (false                                -> FetchId.StringId("boolFetch")) -> Some(false)
          ),
          Set(boolFetch.wrappedId)
        ),
        List(expectedMap, expectedMap, expectedMap)
      )
    )

    val checkCounter = IO(assertEquals(counter, 1))

    checkResult >> checkCounter
  }

  test("Dedupes AllFetch requests (parallel)".only) {
    var counter = 0

    val boolFetch = AllFetch.fromExisting(Fetch.echo[IO, Boolean]("boolFetch"))(IO {
      counter = counter + 1
      Map(true -> true, false -> false)
    })

    val results =
      List(boolFetch.batchAllLazy, boolFetch.batchAllLazy, boolFetch.batchAllLazy).parSequence.run

    val expectedMap = Map(true -> true, false -> false)

    val checkResult = results.assertEquals(
      DedupedRequest[IO, List[Map[Boolean, Boolean]]](
        FetchCache(
          Map(
            (true, FetchId.StringId("boolFetch")) -> Some(true),
            (false                                -> FetchId.StringId("boolFetch")) -> Some(false)
          ),
          Set(boolFetch.wrappedId)
        ),
        List(expectedMap, expectedMap, expectedMap)
      )
    )

    val checkCounter = IO(assertEquals(counter, 1))

    checkResult >> checkCounter
  }
}
