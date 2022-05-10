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
      List(Some(1), Some(2), Some(3))
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
        FetchCache(
          is.toList.map(i => ((i -> dummyFetch.wrappedId) -> Some(i))).toMap,
          Set.empty
        )
      val results = toResults(is)
      DedupedRequest[Id, Map[Int, Int]](cache, results)
    }

    // fetchAllMap
    assertEquals(two.fetchAllMap(dummyFetch), toResults(two.productIterator.toSet))
    assertEquals(three.fetchAllMap(dummyFetch), toResults(three.productIterator.toSet))
    assertEquals(four.fetchAllMap(dummyFetch), toResults(four.productIterator.toSet))
    assertEquals(five.fetchAllMap(dummyFetch), toResults(five.productIterator.toSet))
    assertEquals(six.fetchAllMap(dummyFetch), toResults(six.productIterator.toSet))
    assertEquals(
      seven.fetchAllMap(dummyFetch),
      toResults(seven.productIterator.toSet)
    )
    assertEquals(
      eight.fetchAllMap(dummyFetch),
      toResults(eight.productIterator.toSet)
    )
    assertEquals(
      nine.fetchAllMap(dummyFetch),
      toResults(nine.productIterator.toSet)
    )
    assertEquals(
      ten.fetchAllMap(dummyFetch),
      toResults(ten.productIterator.toSet)
    )
    assertEquals(
      eleven.fetchAllMap(dummyFetch),
      toResults(eleven.productIterator.toSet)
    )
    assertEquals(
      twelve.fetchAllMap(dummyFetch),
      toResults(twelve.productIterator.toSet)
    )
    assertEquals(
      thirteen.fetchAllMap(dummyFetch),
      toResults(thirteen.productIterator.toSet)
    )
    assertEquals(
      fourteen.fetchAllMap(dummyFetch),
      toResults(fourteen.productIterator.toSet)
    )
    assertEquals(
      fifteen.fetchAllMap(dummyFetch),
      toResults(fifteen.productIterator.toSet)
    )
    assertEquals(
      sixteen.fetchAllMap(dummyFetch),
      toResults(sixteen.productIterator.toSet)
    )
    assertEquals(
      seventeen.fetchAllMap(dummyFetch),
      toResults(seventeen.productIterator.toSet)
    )
    assertEquals(
      eighteen.fetchAllMap(dummyFetch),
      toResults(eighteen.productIterator.toSet)
    )
    assertEquals(
      nineteen.fetchAllMap(dummyFetch),
      toResults(nineteen.productIterator.toSet)
    )
    assertEquals(
      twenty.fetchAllMap(dummyFetch),
      toResults(twenty.productIterator.toSet)
    )
    assertEquals(
      twentyOne.fetchAllMap(dummyFetch),
      toResults(twentyOne.productIterator.toSet)
    )
    assertEquals(
      twentyTwo.fetchAllMap(dummyFetch),
      toResults(twentyTwo.productIterator.toSet)
    )

    // fetchAllDedupeMap
    assertEquals(two.fetchAllDedupeMap(dummyFetch), toDF(two.productIterator.toSet))
    assertEquals(three.fetchAllDedupeMap(dummyFetch), toDF(three.productIterator.toSet))
    assertEquals(four.fetchAllDedupeMap(dummyFetch), toDF(four.productIterator.toSet))
    assertEquals(five.fetchAllDedupeMap(dummyFetch), toDF(five.productIterator.toSet))
    assertEquals(six.fetchAllDedupeMap(dummyFetch), toDF(six.productIterator.toSet))
    assertEquals(
      seven.fetchAllDedupeMap(dummyFetch),
      toDF(seven.productIterator.toSet)
    )
    assertEquals(
      eight.fetchAllDedupeMap(dummyFetch),
      toDF(eight.productIterator.toSet)
    )
    assertEquals(
      nine.fetchAllDedupeMap(dummyFetch),
      toDF(nine.productIterator.toSet)
    )
    assertEquals(
      ten.fetchAllDedupeMap(dummyFetch),
      toDF(ten.productIterator.toSet)
    )
    assertEquals(
      eleven.fetchAllDedupeMap(dummyFetch),
      toDF(eleven.productIterator.toSet)
    )
    assertEquals(
      twelve.fetchAllDedupeMap(dummyFetch),
      toDF(twelve.productIterator.toSet)
    )
    assertEquals(
      thirteen.fetchAllDedupeMap(dummyFetch),
      toDF(thirteen.productIterator.toSet)
    )
    assertEquals(
      fourteen.fetchAllDedupeMap(dummyFetch),
      toDF(fourteen.productIterator.toSet)
    )
    assertEquals(
      fifteen.fetchAllDedupeMap(dummyFetch),
      toDF(fifteen.productIterator.toSet)
    )
    assertEquals(
      sixteen.fetchAllDedupeMap(dummyFetch),
      toDF(sixteen.productIterator.toSet)
    )
    assertEquals(
      seventeen.fetchAllDedupeMap(dummyFetch),
      toDF(seventeen.productIterator.toSet)
    )
    assertEquals(
      eighteen.fetchAllDedupeMap(dummyFetch),
      toDF(eighteen.productIterator.toSet)
    )
    assertEquals(
      nineteen.fetchAllDedupeMap(dummyFetch),
      toDF(nineteen.productIterator.toSet)
    )
    assertEquals(
      twenty.fetchAllDedupeMap(dummyFetch),
      toDF(twenty.productIterator.toSet)
    )
    assertEquals(
      twentyOne.fetchAllDedupeMap(dummyFetch),
      toDF(twentyOne.productIterator.toSet)
    )
    assertEquals(
      twentyTwo.fetchAllDedupeMap(dummyFetch),
      toDF(twentyTwo.productIterator.toSet)
    )

    // fetchAllLazyMap
    assertEquals(
      two.fetchAllLazyMap(Applicative[Id], dummyFetch).run,
      toDF(two.productIterator.toSet)
    )
    assertEquals(
      three.fetchAllLazyMap(Applicative[Id], dummyFetch).run,
      toDF(three.productIterator.toSet)
    )
    assertEquals(
      four.fetchAllLazyMap(Applicative[Id], dummyFetch).run,
      toDF(four.productIterator.toSet)
    )
    assertEquals(
      five.fetchAllLazyMap(Applicative[Id], dummyFetch).run,
      toDF(five.productIterator.toSet)
    )
    assertEquals(
      six.fetchAllLazyMap(Applicative[Id], dummyFetch).run,
      toDF(six.productIterator.toSet)
    )
    assertEquals(
      seven.fetchAllLazyMap(Applicative[Id], dummyFetch).run,
      toDF(seven.productIterator.toSet)
    )
    assertEquals(
      eight.fetchAllLazyMap(Applicative[Id], dummyFetch).run,
      toDF(eight.productIterator.toSet)
    )
    assertEquals(
      nine.fetchAllLazyMap(Applicative[Id], dummyFetch).run,
      toDF(nine.productIterator.toSet)
    )
    assertEquals(
      ten.fetchAllLazyMap(Applicative[Id], dummyFetch).run,
      toDF(ten.productIterator.toSet)
    )
    assertEquals(
      eleven.fetchAllLazyMap(Applicative[Id], dummyFetch).run,
      toDF(eleven.productIterator.toSet)
    )
    assertEquals(
      twelve.fetchAllLazyMap(Applicative[Id], dummyFetch).run,
      toDF(twelve.productIterator.toSet)
    )
    assertEquals(
      thirteen.fetchAllLazyMap(Applicative[Id], dummyFetch).run,
      toDF(thirteen.productIterator.toSet)
    )
    assertEquals(
      fourteen.fetchAllLazyMap(Applicative[Id], dummyFetch).run,
      toDF(fourteen.productIterator.toSet)
    )
    assertEquals(
      fifteen.fetchAllLazyMap(Applicative[Id], dummyFetch).run,
      toDF(fifteen.productIterator.toSet)
    )
    assertEquals(
      sixteen.fetchAllLazyMap(Applicative[Id], dummyFetch).run,
      toDF(sixteen.productIterator.toSet)
    )
    assertEquals(
      seventeen.fetchAllLazyMap(Applicative[Id], dummyFetch).run,
      toDF(seventeen.productIterator.toSet)
    )
    assertEquals(
      eighteen.fetchAllLazyMap(Applicative[Id], dummyFetch).run,
      toDF(eighteen.productIterator.toSet)
    )
    assertEquals(
      nineteen.fetchAllLazyMap(Applicative[Id], dummyFetch).run,
      toDF(nineteen.productIterator.toSet)
    )
    assertEquals(
      twenty.fetchAllLazyMap(Applicative[Id], dummyFetch).run,
      toDF(twenty.productIterator.toSet)
    )
    assertEquals(
      twentyOne.fetchAllLazyMap(Applicative[Id], dummyFetch).run,
      toDF(twentyOne.productIterator.toSet)
    )
    assertEquals(
      twentyTwo.fetchAllLazyMap(Applicative[Id], dummyFetch).run,
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
  }
}
