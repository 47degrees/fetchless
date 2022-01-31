package fetchless

import syntax._

import cats.syntax.all._
import cats._
import cats.effect.IO

import cats.effect.unsafe.implicits.global
import cats.effect.kernel.Ref
import munit.CatsEffectSuite
import cats.effect.Clock

class LazySpec extends CatsEffectSuite {

  test("LazyFetch allows for non-linear deduping") {
    def intFetch(countRef: Ref[IO, Int], logRef: Ref[IO, List[Int]]) =
      Fetch.singleSequenced[IO, Int, Int]("intFetch")(i =>
        countRef.update(_ + 1) >> logRef.update(_ :+ i) >> IO(i.some)
      )

    val firstProgram = (IO.ref(0), IO.ref(List.empty[Int])).tupled.flatMap {
      case (countRef, logRef) =>
        implicit val fetch = intFetch(countRef, logRef)

        val fetchProgram = fetch.singleLazy(1) >> fetch.singleLazy(2) >> fetch.singleLazy(2)
        val testProgram  = fetchProgram.run >> (countRef.get, logRef.get).tupled

        testProgram.assertEquals(2 -> List(1, 2)) // We never fetch 2 more than once
    }

    val secondProgram = (IO.ref(0), IO.ref(List.empty[Int])).tupled.flatMap {
      case (countRef, logRef) =>
        implicit val fetch = intFetch(countRef, logRef)

        val fetchProgram = (fetch.singleLazy(1) >> fetch.singleLazy(2) >> fetch
          .singleLazy(2)).preFetch[Int, Int](1).preFetch[Int, Int](2)

        val testProgram = fetchProgram.run >> (countRef.get, logRef.get).tupled
        testProgram.assertEquals(2 -> List(2, 1)) // We pre-fetch 2 before 1
    }

    firstProgram >> secondProgram
  }
}
