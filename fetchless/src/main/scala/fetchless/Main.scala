package fetchless

import cats.effect.IOApp
import cats.effect.{ExitCode, IO}
import cats.syntax.all._
import syntax._
import cats.effect.Clock
import cats.data.Chain
import scala.concurrent.duration.FiniteDuration

object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] = {
    val baseFetch: Fetch[IO, Int, Int] =
      Fetch.singleSequenced[IO, Int, Int]("testFetch")(i => IO.pure(Some(i)))

    def runTest(implicit testFetch: Fetch[IO, Int, Int]) = {
      val initList = (1 to 50000).toList

      val testProgram = initList.traverse_(i => testFetch.singleLazy(i)).run

      val testProgram4 =
        initList.parTraverse_(i => testFetch.singleLazy(i)).run

      val testProgram3 =
        initList.traverse_(i => testFetch.singleDedupe(i))

      val testProgram6 =
        initList.traverse_(i => testFetch.single(i))

      def collectAvg(f: IO[Unit], n: Int): IO[Double] =
        IO.ref(Chain.empty[Long]).flatMap { ref =>
          val addResult = for {
            start <- Clock[IO].monotonic
            _     <- f
            end   <- Clock[IO].monotonic
            _     <- ref.update(_.append((end - start).length))
          } yield ()

          val getAvg = ref.get.map(_.sumAll.toDouble / n)

          addResult.replicateA_(n) >> getAvg
        }

      for {
        time6 <- collectAvg(testProgram6, 40)
        time4 <- collectAvg(testProgram4.void, 40)
        time3 <- collectAvg(testProgram3, 40)
        time1 <- collectAvg(testProgram.void, 40)
        _     <- IO.println("Immediate fetch traverse result")
        _     <- IO.println(time6)
        _     <- IO.println("Immediate deduped fetch traverse result")
        _     <- IO.println(time3)
        _     <- IO.println("LazyRequest traverse result")
        _     <- IO.println(time1)
        _     <- IO.println("LazyRequest parTraverse result")
        _     <- IO.println(time4)
      } yield ExitCode.Success
    }

    val printBar = IO.println("- - - - - - - - - -")
    printBar >>
      IO.println("Without timer") >>
      printBar >>
      runTest(baseFetch) >>
      IO.println("") >>
      printBar >>
      IO.println("With timer") >>
      printBar >>
      runTest(Fetch.withTimer(baseFetch))
  }
}
