package fetchless

import syntax._

import cats.syntax.all._
import cats._
import munit.FunSuite
import cats.data.Chain

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
          Set.empty,
          Chain.empty
        )
      val results = toResults(is)
      DedupedRequest[Id, Map[Int, Int]](cache, results)
    }

    def compareDF[A](a: DedupedRequest[Id, A], b: DedupedRequest[Id, A]) = {
      assertEquals(
        a.unsafeCache.cacheMap,
        b.unsafeCache.cacheMap
      )
      assertEquals(
        a.unsafeCache.fetchAllAcc,
        b.unsafeCache.fetchAllAcc
      )
      // TODO: compare logs?
      // TODO: elsewhere, test timing works for everything
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
    compareDF(two.fetchAllDedupeMap(dummyFetch), toDF(two.productIterator.toSet))
    compareDF(three.fetchAllDedupeMap(dummyFetch), toDF(three.productIterator.toSet))
    compareDF(four.fetchAllDedupeMap(dummyFetch), toDF(four.productIterator.toSet))
    compareDF(five.fetchAllDedupeMap(dummyFetch), toDF(five.productIterator.toSet))
    compareDF(six.fetchAllDedupeMap(dummyFetch), toDF(six.productIterator.toSet))
    compareDF(
      seven.fetchAllDedupeMap(dummyFetch),
      toDF(seven.productIterator.toSet)
    )
    compareDF(
      eight.fetchAllDedupeMap(dummyFetch),
      toDF(eight.productIterator.toSet)
    )
    compareDF(
      nine.fetchAllDedupeMap(dummyFetch),
      toDF(nine.productIterator.toSet)
    )
    compareDF(
      ten.fetchAllDedupeMap(dummyFetch),
      toDF(ten.productIterator.toSet)
    )
    compareDF(
      eleven.fetchAllDedupeMap(dummyFetch),
      toDF(eleven.productIterator.toSet)
    )
    compareDF(
      twelve.fetchAllDedupeMap(dummyFetch),
      toDF(twelve.productIterator.toSet)
    )
    compareDF(
      thirteen.fetchAllDedupeMap(dummyFetch),
      toDF(thirteen.productIterator.toSet)
    )
    compareDF(
      fourteen.fetchAllDedupeMap(dummyFetch),
      toDF(fourteen.productIterator.toSet)
    )
    compareDF(
      fifteen.fetchAllDedupeMap(dummyFetch),
      toDF(fifteen.productIterator.toSet)
    )
    compareDF(
      sixteen.fetchAllDedupeMap(dummyFetch),
      toDF(sixteen.productIterator.toSet)
    )
    compareDF(
      seventeen.fetchAllDedupeMap(dummyFetch),
      toDF(seventeen.productIterator.toSet)
    )
    compareDF(
      eighteen.fetchAllDedupeMap(dummyFetch),
      toDF(eighteen.productIterator.toSet)
    )
    compareDF(
      nineteen.fetchAllDedupeMap(dummyFetch),
      toDF(nineteen.productIterator.toSet)
    )
    compareDF(
      twenty.fetchAllDedupeMap(dummyFetch),
      toDF(twenty.productIterator.toSet)
    )
    compareDF(
      twentyOne.fetchAllDedupeMap(dummyFetch),
      toDF(twentyOne.productIterator.toSet)
    )
    compareDF(
      twentyTwo.fetchAllDedupeMap(dummyFetch),
      toDF(twentyTwo.productIterator.toSet)
    )

    // fetchAllLazyMap
    compareDF(
      two.fetchAllLazyMap(Applicative[Id], dummyFetch).run,
      toDF(two.productIterator.toSet)
    )
    compareDF(
      three.fetchAllLazyMap(Applicative[Id], dummyFetch).run,
      toDF(three.productIterator.toSet)
    )
    compareDF(
      four.fetchAllLazyMap(Applicative[Id], dummyFetch).run,
      toDF(four.productIterator.toSet)
    )
    compareDF(
      five.fetchAllLazyMap(Applicative[Id], dummyFetch).run,
      toDF(five.productIterator.toSet)
    )
    compareDF(
      six.fetchAllLazyMap(Applicative[Id], dummyFetch).run,
      toDF(six.productIterator.toSet)
    )
    compareDF(
      seven.fetchAllLazyMap(Applicative[Id], dummyFetch).run,
      toDF(seven.productIterator.toSet)
    )
    compareDF(
      eight.fetchAllLazyMap(Applicative[Id], dummyFetch).run,
      toDF(eight.productIterator.toSet)
    )
    compareDF(
      nine.fetchAllLazyMap(Applicative[Id], dummyFetch).run,
      toDF(nine.productIterator.toSet)
    )
    compareDF(
      ten.fetchAllLazyMap(Applicative[Id], dummyFetch).run,
      toDF(ten.productIterator.toSet)
    )
    compareDF(
      eleven.fetchAllLazyMap(Applicative[Id], dummyFetch).run,
      toDF(eleven.productIterator.toSet)
    )
    compareDF(
      twelve.fetchAllLazyMap(Applicative[Id], dummyFetch).run,
      toDF(twelve.productIterator.toSet)
    )
    compareDF(
      thirteen.fetchAllLazyMap(Applicative[Id], dummyFetch).run,
      toDF(thirteen.productIterator.toSet)
    )
    compareDF(
      fourteen.fetchAllLazyMap(Applicative[Id], dummyFetch).run,
      toDF(fourteen.productIterator.toSet)
    )
    compareDF(
      fifteen.fetchAllLazyMap(Applicative[Id], dummyFetch).run,
      toDF(fifteen.productIterator.toSet)
    )
    compareDF(
      sixteen.fetchAllLazyMap(Applicative[Id], dummyFetch).run,
      toDF(sixteen.productIterator.toSet)
    )
    compareDF(
      seventeen.fetchAllLazyMap(Applicative[Id], dummyFetch).run,
      toDF(seventeen.productIterator.toSet)
    )
    compareDF(
      eighteen.fetchAllLazyMap(Applicative[Id], dummyFetch).run,
      toDF(eighteen.productIterator.toSet)
    )
    compareDF(
      nineteen.fetchAllLazyMap(Applicative[Id], dummyFetch).run,
      toDF(nineteen.productIterator.toSet)
    )
    compareDF(
      twenty.fetchAllLazyMap(Applicative[Id], dummyFetch).run,
      toDF(twenty.productIterator.toSet)
    )
    compareDF(
      twentyOne.fetchAllLazyMap(Applicative[Id], dummyFetch).run,
      toDF(twentyOne.productIterator.toSet)
    )
    compareDF(
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
    compareDF(
      two.fetchTupledDedupe(dummyFetch),
      toDF(two.productIterator.toSet).as((1.some, 2.some))
    )
    compareDF(
      three.fetchTupledDedupe(dummyFetch),
      toDF(three.productIterator.toSet).as((1.some, 2.some, 3.some))
    )
    compareDF(
      four.fetchTupledDedupe(dummyFetch),
      toDF(four.productIterator.toSet).as((1.some, 2.some, 3.some, 4.some))
    )
    compareDF(
      five.fetchTupledDedupe(dummyFetch),
      toDF(five.productIterator.toSet).as((1.some, 2.some, 3.some, 4.some, 5.some))
    )
    compareDF(
      six.fetchTupledDedupe(dummyFetch),
      toDF(six.productIterator.toSet).as((1.some, 2.some, 3.some, 4.some, 5.some, 6.some))
    )
    compareDF(
      seven.fetchTupledDedupe(dummyFetch),
      toDF(seven.productIterator.toSet).as((1.some, 2.some, 3.some, 4.some, 5.some, 6.some, 7.some))
    )
    compareDF(
      eight.fetchTupledDedupe(dummyFetch),
      toDF(eight.productIterator.toSet)
        .as((1.some, 2.some, 3.some, 4.some, 5.some, 6.some, 7.some, 8.some))
    )
    compareDF(
      nine.fetchTupledDedupe(dummyFetch),
      toDF(nine.productIterator.toSet)
        .as((1.some, 2.some, 3.some, 4.some, 5.some, 6.some, 7.some, 8.some, 9.some))
    )
    compareDF(
      ten.fetchTupledDedupe(dummyFetch),
      toDF(ten.productIterator.toSet)
        .as((1.some, 2.some, 3.some, 4.some, 5.some, 6.some, 7.some, 8.some, 9.some, 10.some))
    )
    compareDF(
      eleven.fetchTupledDedupe(dummyFetch),
      toDF(eleven.productIterator.toSet).as(
        (1.some, 2.some, 3.some, 4.some, 5.some, 6.some, 7.some, 8.some, 9.some, 10.some, 11.some)
      )
    )
    compareDF(
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
    compareDF(
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
    compareDF(
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
    compareDF(
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
    compareDF(
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
    compareDF(
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
    compareDF(
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
    compareDF(
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
    compareDF(
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
    compareDF(
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
    compareDF(
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
    compareDF(
      two.fetchTupledLazy(dummyFetch).run,
      toDF(two.productIterator.toSet).as((1.some, 2.some))
    )
    compareDF(
      three.fetchTupledLazy(dummyFetch).run,
      toDF(three.productIterator.toSet).as((1.some, 2.some, 3.some))
    )
    compareDF(
      four.fetchTupledLazy(dummyFetch).run,
      toDF(four.productIterator.toSet).as((1.some, 2.some, 3.some, 4.some))
    )
    compareDF(
      five.fetchTupledLazy(dummyFetch).run,
      toDF(five.productIterator.toSet).as((1.some, 2.some, 3.some, 4.some, 5.some))
    )
    compareDF(
      six.fetchTupledLazy(dummyFetch).run,
      toDF(six.productIterator.toSet).as((1.some, 2.some, 3.some, 4.some, 5.some, 6.some))
    )
    compareDF(
      seven.fetchTupledLazy(dummyFetch).run,
      toDF(seven.productIterator.toSet).as((1.some, 2.some, 3.some, 4.some, 5.some, 6.some, 7.some))
    )
    compareDF(
      eight.fetchTupledLazy(dummyFetch).run,
      toDF(eight.productIterator.toSet)
        .as((1.some, 2.some, 3.some, 4.some, 5.some, 6.some, 7.some, 8.some))
    )
    compareDF(
      nine.fetchTupledLazy(dummyFetch).run,
      toDF(nine.productIterator.toSet)
        .as((1.some, 2.some, 3.some, 4.some, 5.some, 6.some, 7.some, 8.some, 9.some))
    )
    compareDF(
      ten.fetchTupledLazy(dummyFetch).run,
      toDF(ten.productIterator.toSet)
        .as((1.some, 2.some, 3.some, 4.some, 5.some, 6.some, 7.some, 8.some, 9.some, 10.some))
    )
    compareDF(
      eleven.fetchTupledLazy(dummyFetch).run,
      toDF(eleven.productIterator.toSet).as(
        (1.some, 2.some, 3.some, 4.some, 5.some, 6.some, 7.some, 8.some, 9.some, 10.some, 11.some)
      )
    )
    compareDF(
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
    compareDF(
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
    compareDF(
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
    compareDF(
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
    compareDF(
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
    compareDF(
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
    compareDF(
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
    compareDF(
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
    compareDF(
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
    compareDF(
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
    compareDF(
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
