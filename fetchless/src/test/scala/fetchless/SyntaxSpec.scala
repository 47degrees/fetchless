package fetchless

import syntax._

import cats.syntax.all._
import cats._
import munit.FunSuite

class SyntaxSpec extends FunSuite {

  implicit val dummyFetch =
    Fetch.echo[Id, Int]("intFetch")

  test("List batching") {
    val result = List(1, 2, 3).fetchAll(dummyFetch)
    assertEquals(
      result,
      Map(
        1 -> 1,
        2 -> 2,
        3 -> 3
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
    // format:off
    val two       = (1, 2)
    val three     = (1, 2, 3)
    val four      = (1, 2, 3, 4)
    val five      = (1, 2, 3, 4, 5)
    val six       = (1, 2, 3, 4, 5, 6)
    val seven     = (1, 2, 3, 4, 5, 6, 7)
    val eight     = (1, 2, 3, 4, 5, 6, 7, 8)
    val nine      = (1, 2, 3, 4, 5, 6, 7, 8, 9)
    val ten       = (1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
    val eleven    = (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)
    val twelve    = (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
    val thirteen  = (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13)
    val fourteen  = (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14)
    val fifteen   = (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)
    val sixteen   = (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16)
    val seventeen = (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17)
    val eighteen  = (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18)
    val nineteen  = (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19)
    val twenty    = (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20)
    val twentyOne = (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21)
    val twentyTwo = (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22)

    def toResults(is: Set[Any]) =
      is.toList.map(i => i -> i).toMap[Any, Any].asInstanceOf[Map[Int, Int]]

    def toDF(is: Set[Any]) = {
      val cache =
        is.toList.map(i => ((i -> "intFetch") -> Some(i))).toMap[(Any, String), Option[Any]]
      val results = toResults(is)
      DedupedFetch[Id, Map[Int, Int]](cache, results)
    }

    // fetchAll
    assertEquals(two.fetchAll(dummyFetch), toResults(two.productIterator.toSet))
    assertEquals(three.fetchAll(dummyFetch), toResults(three.productIterator.toSet))
    assertEquals(four.fetchAll(dummyFetch), toResults(four.productIterator.toSet))
    assertEquals(five.fetchAll(dummyFetch), toResults(five.productIterator.toSet))
    assertEquals(six.fetchAll(dummyFetch), toResults(six.productIterator.toSet))
    assertEquals(
      seven.fetchAll(dummyFetch),
      toResults(seven.productIterator.toSet)
    )
    assertEquals(
      eight.fetchAll(dummyFetch),
      toResults(eight.productIterator.toSet)
    )
    assertEquals(
      nine.fetchAll(dummyFetch),
      toResults(nine.productIterator.toSet)
    )
    assertEquals(
      ten.fetchAll(dummyFetch),
      toResults(ten.productIterator.toSet)
    )
    assertEquals(
      eleven.fetchAll(dummyFetch),
      toResults(eleven.productIterator.toSet)
    )
    assertEquals(
      twelve.fetchAll(dummyFetch),
      toResults(twelve.productIterator.toSet)
    )
    assertEquals(
      thirteen.fetchAll(dummyFetch),
      toResults(thirteen.productIterator.toSet)
    )
    assertEquals(
      fourteen.fetchAll(dummyFetch),
      toResults(fourteen.productIterator.toSet)
    )
    assertEquals(
      fifteen.fetchAll(dummyFetch),
      toResults(fifteen.productIterator.toSet)
    )
    assertEquals(
      sixteen.fetchAll(dummyFetch),
      toResults(sixteen.productIterator.toSet)
    )
    assertEquals(
      seventeen.fetchAll(dummyFetch),
      toResults(seventeen.productIterator.toSet)
    )
    assertEquals(
      eighteen.fetchAll(dummyFetch),
      toResults(eighteen.productIterator.toSet)
    )
    assertEquals(
      nineteen.fetchAll(dummyFetch),
      toResults(nineteen.productIterator.toSet)
    )
    assertEquals(
      twenty.fetchAll(dummyFetch),
      toResults(twenty.productIterator.toSet)
    )
    assertEquals(
      twentyOne.fetchAll(dummyFetch),
      toResults(twentyOne.productIterator.toSet)
    )
    assertEquals(
      twentyTwo.fetchAll(dummyFetch),
      toResults(twentyTwo.productIterator.toSet)
    )

    // fetchAllDedupe
    assertEquals(two.fetchAllDedupe(dummyFetch), toDF(two.productIterator.toSet))
    assertEquals(three.fetchAllDedupe(dummyFetch), toDF(three.productIterator.toSet))
    assertEquals(four.fetchAllDedupe(dummyFetch), toDF(four.productIterator.toSet))
    assertEquals(five.fetchAllDedupe(dummyFetch), toDF(five.productIterator.toSet))
    assertEquals(six.fetchAllDedupe(dummyFetch), toDF(six.productIterator.toSet))
    assertEquals(
      seven.fetchAllDedupe(dummyFetch),
      toDF(seven.productIterator.toSet)
    )
    assertEquals(
      eight.fetchAllDedupe(dummyFetch),
      toDF(eight.productIterator.toSet)
    )
    assertEquals(
      nine.fetchAllDedupe(dummyFetch),
      toDF(nine.productIterator.toSet)
    )
    assertEquals(
      ten.fetchAllDedupe(dummyFetch),
      toDF(ten.productIterator.toSet)
    )
    assertEquals(
      eleven.fetchAllDedupe(dummyFetch),
      toDF(eleven.productIterator.toSet)
    )
    assertEquals(
      twelve.fetchAllDedupe(dummyFetch),
      toDF(twelve.productIterator.toSet)
    )
    assertEquals(
      thirteen.fetchAllDedupe(dummyFetch),
      toDF(thirteen.productIterator.toSet)
    )
    assertEquals(
      fourteen.fetchAllDedupe(dummyFetch),
      toDF(fourteen.productIterator.toSet)
    )
    assertEquals(
      fifteen.fetchAllDedupe(dummyFetch),
      toDF(fifteen.productIterator.toSet)
    )
    assertEquals(
      sixteen.fetchAllDedupe(dummyFetch),
      toDF(sixteen.productIterator.toSet)
    )
    assertEquals(
      seventeen.fetchAllDedupe(dummyFetch),
      toDF(seventeen.productIterator.toSet)
    )
    assertEquals(
      eighteen.fetchAllDedupe(dummyFetch),
      toDF(eighteen.productIterator.toSet)
    )
    assertEquals(
      nineteen.fetchAllDedupe(dummyFetch),
      toDF(nineteen.productIterator.toSet)
    )
    assertEquals(
      twenty.fetchAllDedupe(dummyFetch),
      toDF(twenty.productIterator.toSet)
    )
    assertEquals(
      twentyOne.fetchAllDedupe(dummyFetch),
      toDF(twentyOne.productIterator.toSet)
    )
    assertEquals(
      twentyTwo.fetchAllDedupe(dummyFetch),
      toDF(twentyTwo.productIterator.toSet)
    )

    // fetchAllLazy
    assertEquals(two.fetchAllLazy(dummyFetch).run, toDF(two.productIterator.toSet))
    assertEquals(three.fetchAllLazy(dummyFetch).run, toDF(three.productIterator.toSet))
    assertEquals(four.fetchAllLazy(dummyFetch).run, toDF(four.productIterator.toSet))
    assertEquals(five.fetchAllLazy(dummyFetch).run, toDF(five.productIterator.toSet))
    assertEquals(six.fetchAllLazy(dummyFetch).run, toDF(six.productIterator.toSet))
    assertEquals(
      seven.fetchAllLazy(dummyFetch).run,
      toDF(seven.productIterator.toSet)
    )
    assertEquals(
      eight.fetchAllLazy(dummyFetch).run,
      toDF(eight.productIterator.toSet)
    )
    assertEquals(
      nine.fetchAllLazy(dummyFetch).run,
      toDF(nine.productIterator.toSet)
    )
    assertEquals(
      ten.fetchAllLazy(dummyFetch).run,
      toDF(ten.productIterator.toSet)
    )
    assertEquals(
      eleven.fetchAllLazy(dummyFetch).run,
      toDF(eleven.productIterator.toSet)
    )
    assertEquals(
      twelve.fetchAllLazy(dummyFetch).run,
      toDF(twelve.productIterator.toSet)
    )
    assertEquals(
      thirteen.fetchAllLazy(dummyFetch).run,
      toDF(thirteen.productIterator.toSet)
    )
    assertEquals(
      fourteen.fetchAllLazy(dummyFetch).run,
      toDF(fourteen.productIterator.toSet)
    )
    assertEquals(
      fifteen.fetchAllLazy(dummyFetch).run,
      toDF(fifteen.productIterator.toSet)
    )
    assertEquals(
      sixteen.fetchAllLazy(dummyFetch).run,
      toDF(sixteen.productIterator.toSet)
    )
    assertEquals(
      seventeen.fetchAllLazy(dummyFetch).run,
      toDF(seventeen.productIterator.toSet)
    )
    assertEquals(
      eighteen.fetchAllLazy(dummyFetch).run,
      toDF(eighteen.productIterator.toSet)
    )
    assertEquals(
      nineteen.fetchAllLazy(dummyFetch).run,
      toDF(nineteen.productIterator.toSet)
    )
    assertEquals(
      twenty.fetchAllLazy(dummyFetch).run,
      toDF(twenty.productIterator.toSet)
    )
    assertEquals(
      twentyOne.fetchAllLazy(dummyFetch).run,
      toDF(twentyOne.productIterator.toSet)
    )
    assertEquals(
      twentyTwo.fetchAllLazy(dummyFetch).run,
      toDF(twentyTwo.productIterator.toSet)
    )

    // fetchTupled
    assertEquals(two.fetchTupled(dummyFetch), (1.some, 2.some))
    assertEquals(three.fetchTupled(dummyFetch), (1.some, 2.some, 3.some))
    assertEquals(four.fetchTupled(dummyFetch), (1.some, 2.some, 3.some, 4.some))
    assertEquals(five.fetchTupled(dummyFetch), (1.some, 2.some, 3.some, 4.some, 5.some))
    assertEquals(six.fetchTupled(dummyFetch), (1.some, 2.some, 3.some, 4.some, 5.some, 6.some))
    assertEquals(
      seven.fetchTupled(dummyFetch),
      (1.some, 2.some, 3.some, 4.some, 5.some, 6.some, 7.some)
    )
    assertEquals(
      eight.fetchTupled(dummyFetch),
      (1.some, 2.some, 3.some, 4.some, 5.some, 6.some, 7.some, 8.some)
    )
    assertEquals(
      nine.fetchTupled(dummyFetch),
      (1.some, 2.some, 3.some, 4.some, 5.some, 6.some, 7.some, 8.some, 9.some)
    )
    assertEquals(
      ten.fetchTupled(dummyFetch),
      (1.some, 2.some, 3.some, 4.some, 5.some, 6.some, 7.some, 8.some, 9.some, 10.some)
    )
    assertEquals(
      eleven.fetchTupled(dummyFetch),
      (1.some, 2.some, 3.some, 4.some, 5.some, 6.some, 7.some, 8.some, 9.some, 10.some, 11.some)
    )
    assertEquals(
      twelve.fetchTupled(dummyFetch),
      (
        1.some,
        2.some,
        3.some,
        4.some,
        5.some,
        6.some,
        7.some,
        8.some,
        9.some,
        10.some,
        11.some,
        12.some
      )
    )
    assertEquals(
      thirteen.fetchTupled(dummyFetch),
      (
        1.some,
        2.some,
        3.some,
        4.some,
        5.some,
        6.some,
        7.some,
        8.some,
        9.some,
        10.some,
        11.some,
        12.some,
        13.some
      )
    )
    assertEquals(
      fourteen.fetchTupled(dummyFetch),
      (
        1.some,
        2.some,
        3.some,
        4.some,
        5.some,
        6.some,
        7.some,
        8.some,
        9.some,
        10.some,
        11.some,
        12.some,
        13.some,
        14.some
      )
    )
    assertEquals(
      fifteen.fetchTupled(dummyFetch),
      (
        1.some,
        2.some,
        3.some,
        4.some,
        5.some,
        6.some,
        7.some,
        8.some,
        9.some,
        10.some,
        11.some,
        12.some,
        13.some,
        14.some,
        15.some
      )
    )
    assertEquals(
      sixteen.fetchTupled(dummyFetch),
      (
        1.some,
        2.some,
        3.some,
        4.some,
        5.some,
        6.some,
        7.some,
        8.some,
        9.some,
        10.some,
        11.some,
        12.some,
        13.some,
        14.some,
        15.some,
        16.some
      )
    )
    assertEquals(
      seventeen.fetchTupled(dummyFetch),
      (
        1.some,
        2.some,
        3.some,
        4.some,
        5.some,
        6.some,
        7.some,
        8.some,
        9.some,
        10.some,
        11.some,
        12.some,
        13.some,
        14.some,
        15.some,
        16.some,
        17.some
      )
    )
    assertEquals(
      eighteen.fetchTupled(dummyFetch),
      (
        1.some,
        2.some,
        3.some,
        4.some,
        5.some,
        6.some,
        7.some,
        8.some,
        9.some,
        10.some,
        11.some,
        12.some,
        13.some,
        14.some,
        15.some,
        16.some,
        17.some,
        18.some
      )
    )
    assertEquals(
      nineteen.fetchTupled(dummyFetch),
      (
        1.some,
        2.some,
        3.some,
        4.some,
        5.some,
        6.some,
        7.some,
        8.some,
        9.some,
        10.some,
        11.some,
        12.some,
        13.some,
        14.some,
        15.some,
        16.some,
        17.some,
        18.some,
        19.some
      )
    )
    assertEquals(
      twenty.fetchTupled(dummyFetch),
      (
        1.some,
        2.some,
        3.some,
        4.some,
        5.some,
        6.some,
        7.some,
        8.some,
        9.some,
        10.some,
        11.some,
        12.some,
        13.some,
        14.some,
        15.some,
        16.some,
        17.some,
        18.some,
        19.some,
        20.some
      )
    )
    assertEquals(
      twentyOne.fetchTupled(dummyFetch),
      (
        1.some,
        2.some,
        3.some,
        4.some,
        5.some,
        6.some,
        7.some,
        8.some,
        9.some,
        10.some,
        11.some,
        12.some,
        13.some,
        14.some,
        15.some,
        16.some,
        17.some,
        18.some,
        19.some,
        20.some,
        21.some
      )
    )
    assertEquals(
      twentyTwo.fetchTupled(dummyFetch),
      (
        1.some,
        2.some,
        3.some,
        4.some,
        5.some,
        6.some,
        7.some,
        8.some,
        9.some,
        10.some,
        11.some,
        12.some,
        13.some,
        14.some,
        15.some,
        16.some,
        17.some,
        18.some,
        19.some,
        20.some,
        21.some,
        22.some
      )
    )

    // fetchTupledDedupe
    assertEquals(
      two.fetchTupledDedupe(dummyFetch),
      toDF(two.productIterator.toSet).as((1.some, 2.some))
    )
    assertEquals(
      three.fetchTupledDedupe(dummyFetch),
      toDF(three.productIterator.toSet).as((1.some, 2.some, 3.some))
    )
    assertEquals(
      four.fetchTupledDedupe(dummyFetch),
      toDF(four.productIterator.toSet).as((1.some, 2.some, 3.some, 4.some))
    )
    assertEquals(
      five.fetchTupledDedupe(dummyFetch),
      toDF(five.productIterator.toSet).as((1.some, 2.some, 3.some, 4.some, 5.some))
    )
    assertEquals(
      six.fetchTupledDedupe(dummyFetch),
      toDF(six.productIterator.toSet).as((1.some, 2.some, 3.some, 4.some, 5.some, 6.some))
    )
    assertEquals(
      seven.fetchTupledDedupe(dummyFetch),
      toDF(seven.productIterator.toSet).as((1.some, 2.some, 3.some, 4.some, 5.some, 6.some, 7.some))
    )
    assertEquals(
      eight.fetchTupledDedupe(dummyFetch),
      toDF(eight.productIterator.toSet)
        .as((1.some, 2.some, 3.some, 4.some, 5.some, 6.some, 7.some, 8.some))
    )
    assertEquals(
      nine.fetchTupledDedupe(dummyFetch),
      toDF(nine.productIterator.toSet)
        .as((1.some, 2.some, 3.some, 4.some, 5.some, 6.some, 7.some, 8.some, 9.some))
    )
    assertEquals(
      ten.fetchTupledDedupe(dummyFetch),
      toDF(ten.productIterator.toSet)
        .as((1.some, 2.some, 3.some, 4.some, 5.some, 6.some, 7.some, 8.some, 9.some, 10.some))
    )
    assertEquals(
      eleven.fetchTupledDedupe(dummyFetch),
      toDF(eleven.productIterator.toSet).as(
        (1.some, 2.some, 3.some, 4.some, 5.some, 6.some, 7.some, 8.some, 9.some, 10.some, 11.some)
      )
    )
    assertEquals(
      twelve.fetchTupledDedupe(dummyFetch),
      toDF(twelve.productIterator.toSet).as(
        (
          1.some,
          2.some,
          3.some,
          4.some,
          5.some,
          6.some,
          7.some,
          8.some,
          9.some,
          10.some,
          11.some,
          12.some
        )
      )
    )
    assertEquals(
      thirteen.fetchTupledDedupe(dummyFetch),
      toDF(thirteen.productIterator.toSet).as(
        (
          1.some,
          2.some,
          3.some,
          4.some,
          5.some,
          6.some,
          7.some,
          8.some,
          9.some,
          10.some,
          11.some,
          12.some,
          13.some
        )
      )
    )
    assertEquals(
      fourteen.fetchTupledDedupe(dummyFetch),
      toDF(fourteen.productIterator.toSet).as(
        (
          1.some,
          2.some,
          3.some,
          4.some,
          5.some,
          6.some,
          7.some,
          8.some,
          9.some,
          10.some,
          11.some,
          12.some,
          13.some,
          14.some
        )
      )
    )
    assertEquals(
      fifteen.fetchTupledDedupe(dummyFetch),
      toDF(fifteen.productIterator.toSet).as(
        (
          1.some,
          2.some,
          3.some,
          4.some,
          5.some,
          6.some,
          7.some,
          8.some,
          9.some,
          10.some,
          11.some,
          12.some,
          13.some,
          14.some,
          15.some
        )
      )
    )
    assertEquals(
      sixteen.fetchTupledDedupe(dummyFetch),
      toDF(sixteen.productIterator.toSet).as(
        (
          1.some,
          2.some,
          3.some,
          4.some,
          5.some,
          6.some,
          7.some,
          8.some,
          9.some,
          10.some,
          11.some,
          12.some,
          13.some,
          14.some,
          15.some,
          16.some
        )
      )
    )
    assertEquals(
      seventeen.fetchTupledDedupe(dummyFetch),
      toDF(seventeen.productIterator.toSet).as(
        (
          1.some,
          2.some,
          3.some,
          4.some,
          5.some,
          6.some,
          7.some,
          8.some,
          9.some,
          10.some,
          11.some,
          12.some,
          13.some,
          14.some,
          15.some,
          16.some,
          17.some
        )
      )
    )
    assertEquals(
      eighteen.fetchTupledDedupe(dummyFetch),
      toDF(eighteen.productIterator.toSet).as(
        (
          1.some,
          2.some,
          3.some,
          4.some,
          5.some,
          6.some,
          7.some,
          8.some,
          9.some,
          10.some,
          11.some,
          12.some,
          13.some,
          14.some,
          15.some,
          16.some,
          17.some,
          18.some
        )
      )
    )
    assertEquals(
      nineteen.fetchTupledDedupe(dummyFetch),
      toDF(nineteen.productIterator.toSet).as(
        (
          1.some,
          2.some,
          3.some,
          4.some,
          5.some,
          6.some,
          7.some,
          8.some,
          9.some,
          10.some,
          11.some,
          12.some,
          13.some,
          14.some,
          15.some,
          16.some,
          17.some,
          18.some,
          19.some
        )
      )
    )
    assertEquals(
      twenty.fetchTupledDedupe(dummyFetch),
      toDF(twenty.productIterator.toSet).as(
        (
          1.some,
          2.some,
          3.some,
          4.some,
          5.some,
          6.some,
          7.some,
          8.some,
          9.some,
          10.some,
          11.some,
          12.some,
          13.some,
          14.some,
          15.some,
          16.some,
          17.some,
          18.some,
          19.some,
          20.some
        )
      )
    )
    assertEquals(
      twentyOne.fetchTupledDedupe(dummyFetch),
      toDF(twentyOne.productIterator.toSet).as(
        (
          1.some,
          2.some,
          3.some,
          4.some,
          5.some,
          6.some,
          7.some,
          8.some,
          9.some,
          10.some,
          11.some,
          12.some,
          13.some,
          14.some,
          15.some,
          16.some,
          17.some,
          18.some,
          19.some,
          20.some,
          21.some
        )
      )
    )
    assertEquals(
      twentyTwo.fetchTupledDedupe(dummyFetch),
      toDF(twentyTwo.productIterator.toSet).as(
        (
          1.some,
          2.some,
          3.some,
          4.some,
          5.some,
          6.some,
          7.some,
          8.some,
          9.some,
          10.some,
          11.some,
          12.some,
          13.some,
          14.some,
          15.some,
          16.some,
          17.some,
          18.some,
          19.some,
          20.some,
          21.some,
          22.some
        )
      )
    )

    // fetchTupledLazy
    assertEquals(
      two.fetchTupledLazy(dummyFetch).run,
      toDF(two.productIterator.toSet).as((1.some, 2.some))
    )
    assertEquals(
      three.fetchTupledLazy(dummyFetch).run,
      toDF(three.productIterator.toSet).as((1.some, 2.some, 3.some))
    )
    assertEquals(
      four.fetchTupledLazy(dummyFetch).run,
      toDF(four.productIterator.toSet).as((1.some, 2.some, 3.some, 4.some))
    )
    assertEquals(
      five.fetchTupledLazy(dummyFetch).run,
      toDF(five.productIterator.toSet).as((1.some, 2.some, 3.some, 4.some, 5.some))
    )
    assertEquals(
      six.fetchTupledLazy(dummyFetch).run,
      toDF(six.productIterator.toSet).as((1.some, 2.some, 3.some, 4.some, 5.some, 6.some))
    )
    assertEquals(
      seven.fetchTupledLazy(dummyFetch).run,
      toDF(seven.productIterator.toSet).as((1.some, 2.some, 3.some, 4.some, 5.some, 6.some, 7.some))
    )
    assertEquals(
      eight.fetchTupledLazy(dummyFetch).run,
      toDF(eight.productIterator.toSet)
        .as((1.some, 2.some, 3.some, 4.some, 5.some, 6.some, 7.some, 8.some))
    )
    assertEquals(
      nine.fetchTupledLazy(dummyFetch).run,
      toDF(nine.productIterator.toSet)
        .as((1.some, 2.some, 3.some, 4.some, 5.some, 6.some, 7.some, 8.some, 9.some))
    )
    assertEquals(
      ten.fetchTupledLazy(dummyFetch).run,
      toDF(ten.productIterator.toSet)
        .as((1.some, 2.some, 3.some, 4.some, 5.some, 6.some, 7.some, 8.some, 9.some, 10.some))
    )
    assertEquals(
      eleven.fetchTupledLazy(dummyFetch).run,
      toDF(eleven.productIterator.toSet).as(
        (1.some, 2.some, 3.some, 4.some, 5.some, 6.some, 7.some, 8.some, 9.some, 10.some, 11.some)
      )
    )
    assertEquals(
      twelve.fetchTupledLazy(dummyFetch).run,
      toDF(twelve.productIterator.toSet).as(
        (
          1.some,
          2.some,
          3.some,
          4.some,
          5.some,
          6.some,
          7.some,
          8.some,
          9.some,
          10.some,
          11.some,
          12.some
        )
      )
    )
    assertEquals(
      thirteen.fetchTupledLazy(dummyFetch).run,
      toDF(thirteen.productIterator.toSet).as(
        (
          1.some,
          2.some,
          3.some,
          4.some,
          5.some,
          6.some,
          7.some,
          8.some,
          9.some,
          10.some,
          11.some,
          12.some,
          13.some
        )
      )
    )
    assertEquals(
      fourteen.fetchTupledLazy(dummyFetch).run,
      toDF(fourteen.productIterator.toSet).as(
        (
          1.some,
          2.some,
          3.some,
          4.some,
          5.some,
          6.some,
          7.some,
          8.some,
          9.some,
          10.some,
          11.some,
          12.some,
          13.some,
          14.some
        )
      )
    )
    assertEquals(
      fifteen.fetchTupledLazy(dummyFetch).run,
      toDF(fifteen.productIterator.toSet).as(
        (
          1.some,
          2.some,
          3.some,
          4.some,
          5.some,
          6.some,
          7.some,
          8.some,
          9.some,
          10.some,
          11.some,
          12.some,
          13.some,
          14.some,
          15.some
        )
      )
    )
    assertEquals(
      sixteen.fetchTupledLazy(dummyFetch).run,
      toDF(sixteen.productIterator.toSet).as(
        (
          1.some,
          2.some,
          3.some,
          4.some,
          5.some,
          6.some,
          7.some,
          8.some,
          9.some,
          10.some,
          11.some,
          12.some,
          13.some,
          14.some,
          15.some,
          16.some
        )
      )
    )
    assertEquals(
      seventeen.fetchTupledLazy(dummyFetch).run,
      toDF(seventeen.productIterator.toSet).as(
        (
          1.some,
          2.some,
          3.some,
          4.some,
          5.some,
          6.some,
          7.some,
          8.some,
          9.some,
          10.some,
          11.some,
          12.some,
          13.some,
          14.some,
          15.some,
          16.some,
          17.some
        )
      )
    )
    assertEquals(
      eighteen.fetchTupledLazy(dummyFetch).run,
      toDF(eighteen.productIterator.toSet).as(
        (
          1.some,
          2.some,
          3.some,
          4.some,
          5.some,
          6.some,
          7.some,
          8.some,
          9.some,
          10.some,
          11.some,
          12.some,
          13.some,
          14.some,
          15.some,
          16.some,
          17.some,
          18.some
        )
      )
    )
    assertEquals(
      nineteen.fetchTupledLazy(dummyFetch).run,
      toDF(nineteen.productIterator.toSet).as(
        (
          1.some,
          2.some,
          3.some,
          4.some,
          5.some,
          6.some,
          7.some,
          8.some,
          9.some,
          10.some,
          11.some,
          12.some,
          13.some,
          14.some,
          15.some,
          16.some,
          17.some,
          18.some,
          19.some
        )
      )
    )
    assertEquals(
      twenty.fetchTupledLazy(dummyFetch).run,
      toDF(twenty.productIterator.toSet).as(
        (
          1.some,
          2.some,
          3.some,
          4.some,
          5.some,
          6.some,
          7.some,
          8.some,
          9.some,
          10.some,
          11.some,
          12.some,
          13.some,
          14.some,
          15.some,
          16.some,
          17.some,
          18.some,
          19.some,
          20.some
        )
      )
    )
    assertEquals(
      twentyOne.fetchTupledLazy(dummyFetch).run,
      toDF(twentyOne.productIterator.toSet).as(
        (
          1.some,
          2.some,
          3.some,
          4.some,
          5.some,
          6.some,
          7.some,
          8.some,
          9.some,
          10.some,
          11.some,
          12.some,
          13.some,
          14.some,
          15.some,
          16.some,
          17.some,
          18.some,
          19.some,
          20.some,
          21.some
        )
      )
    )
    assertEquals(
      twentyTwo.fetchTupledLazy(dummyFetch).run,
      toDF(twentyTwo.productIterator.toSet).as(
        (
          1.some,
          2.some,
          3.some,
          4.some,
          5.some,
          6.some,
          7.some,
          8.some,
          9.some,
          10.some,
          11.some,
          12.some,
          13.some,
          14.some,
          15.some,
          16.some,
          17.some,
          18.some,
          19.some,
          20.some,
          21.some,
          22.some
        )
      )
    )

    // format:on

    // TODO: redo syntax internals to cover the implicit methods as well without bulking up this test

  }
}
