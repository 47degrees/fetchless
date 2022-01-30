package fetchless

import cats.{FlatMap, Functor, Traverse}
import cats.syntax.all._

object syntax {

  implicit class SingleSyntax[I](i: I) {
    def fetch[F[_], A](implicit F: Fetch[F, I, A]): F[Option[A]] = F.single(i)
  }

  implicit class EffectfulSyntax[F[_]: FlatMap, I](fi: F[I]) {
    def fetch[A](implicit F: Fetch[F, I, A]): F[Option[A]] = fi.flatMap(F.single(_))
  }

  implicit class TraverseBatchSyntax[G[_]: Traverse, I](is: G[I]) {
    def batchAll[F[_], A](implicit fetch: Fetch[F, I, A]) =
      fetch.batch(is)
  }

  implicit class Tuple2BatchSyntax[I](is: (I, I)) {
    def batchAll[F[_], A](implicit fetch: Fetch[F, I, A]) =
      fetch.batch(is.productIterator.asInstanceOf[Iterator[I]].toSet)
    def batchTupled[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]) =
      fetch.batch(Set(is._1, is._2)).map { m =>
        (m.get(is._1), m.get(is._2))
      }
  }

  implicit class Tuple3BatchSyntax[I](is: (I, I, I)) {
    def batchAll[F[_], A](implicit fetch: Fetch[F, I, A]) =
      fetch.batch(is.productIterator.asInstanceOf[Iterator[I]].toSet)
    def batchTupled[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]) =
      fetch.batch(Set(is._1, is._2, is._3)).map { m =>
        (m.get(is._1), m.get(is._2), m.get(is._3))
      }
  }

  implicit class Tuple4BatchSyntax[I](is: (I, I, I, I)) {
    def batchAll[F[_], A](implicit fetch: Fetch[F, I, A]) =
      fetch.batch(is.productIterator.asInstanceOf[Iterator[I]].toSet)
    def batchTupled[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]) =
      fetch.batch(Set(is._1, is._2, is._3, is._4)).map { m =>
        println("batchin'")
        (m.get(is._1), m.get(is._2), m.get(is._3), m.get(is._4))
      }
  }

  implicit class Tuple5BatchSyntax[I](is: (I, I, I, I, I)) {
    def batchAll[F[_], A](implicit fetch: Fetch[F, I, A]) =
      fetch.batch(is.productIterator.asInstanceOf[Iterator[I]].toSet)
    def batchTupled[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]) =
      fetch.batch(Set(is._1, is._2, is._3, is._4, is._5)).map { m =>
        (m.get(is._1), m.get(is._2), m.get(is._3), m.get(is._4), m.get(is._5))
      }
  }

  implicit class Tuple6BatchSyntax[I](is: (I, I, I, I, I, I)) {
    def batchAll[F[_], A](implicit fetch: Fetch[F, I, A]) =
      fetch.batch(is.productIterator.asInstanceOf[Iterator[I]].toSet)
    def batchTupled[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]) =
      fetch.batch(Set(is._1, is._2, is._3, is._4, is._5, is._6)).map { m =>
        (m.get(is._1), m.get(is._2), m.get(is._3), m.get(is._4), m.get(is._5), m.get(is._6))
      }
  }

  implicit class Tuple7BatchSyntax[I](is: (I, I, I, I, I, I, I)) {
    def batchAll[F[_], A](implicit fetch: Fetch[F, I, A]) =
      fetch.batch(is.productIterator.asInstanceOf[Iterator[I]].toSet)
    def batchTupled[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]) =
      fetch.batch(Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7)).map { m =>
        (
          m.get(is._1),
          m.get(is._2),
          m.get(is._3),
          m.get(is._4),
          m.get(is._5),
          m.get(is._6),
          m.get(is._7)
        )
      }
  }

  implicit class Tuple8BatchSyntax[I](is: (I, I, I, I, I, I, I, I)) {
    def batchAll[F[_], A](implicit fetch: Fetch[F, I, A]) =
      fetch.batch(is.productIterator.asInstanceOf[Iterator[I]].toSet)
    def batchTupled[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]) =
      fetch.batch(Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7, is._8)).map { m =>
        (
          m.get(is._1),
          m.get(is._2),
          m.get(is._3),
          m.get(is._4),
          m.get(is._5),
          m.get(is._6),
          m.get(is._7),
          m.get(is._8)
        )
      }
  }

  implicit class Tuple9BatchSyntax[I](is: (I, I, I, I, I, I, I, I, I)) {
    def batchAll[F[_], A](implicit fetch: Fetch[F, I, A]) =
      fetch.batch(is.productIterator.asInstanceOf[Iterator[I]].toSet)
    def batchTupled[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]) =
      fetch.batch(Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7, is._8, is._9)).map { m =>
        (
          m.get(is._1),
          m.get(is._2),
          m.get(is._3),
          m.get(is._4),
          m.get(is._5),
          m.get(is._6),
          m.get(is._7),
          m.get(is._8),
          m.get(is._9)
        )
      }
  }

  implicit class Tuple10BatchSyntax[I](is: (I, I, I, I, I, I, I, I, I, I)) {
    def batchAll[F[_], A](implicit fetch: Fetch[F, I, A]) =
      fetch.batch(is.productIterator.asInstanceOf[Iterator[I]].toSet)
    def batchTupled[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]) =
      fetch.batch(Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7, is._8, is._9, is._10)).map {
        m =>
          (
            m.get(is._1),
            m.get(is._2),
            m.get(is._3),
            m.get(is._4),
            m.get(is._5),
            m.get(is._6),
            m.get(is._7),
            m.get(is._8),
            m.get(is._9),
            m.get(is._10)
          )
      }
  }

  implicit class Tuple11BatchSyntax[I](is: (I, I, I, I, I, I, I, I, I, I, I)) {
    def batchAll[F[_], A](implicit fetch: Fetch[F, I, A]) =
      fetch.batch(is.productIterator.asInstanceOf[Iterator[I]].toSet)
    def batchTupled[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]) = fetch
      .batch(Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7, is._8, is._9, is._10, is._11))
      .map { m =>
        (
          m.get(is._1),
          m.get(is._2),
          m.get(is._3),
          m.get(is._4),
          m.get(is._5),
          m.get(is._6),
          m.get(is._7),
          m.get(is._8),
          m.get(is._9),
          m.get(is._10),
          m.get(is._11)
        )
      }
  }

  implicit class Tuple12BatchSyntax[I](is: (I, I, I, I, I, I, I, I, I, I, I, I)) {
    def batchAll[F[_], A](implicit fetch: Fetch[F, I, A]) =
      fetch.batch(is.productIterator.asInstanceOf[Iterator[I]].toSet)
    def batchTupled[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]) = fetch
      .batch(
        Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7, is._8, is._9, is._10, is._11, is._12)
      )
      .map { m =>
        (
          m.get(is._1),
          m.get(is._2),
          m.get(is._3),
          m.get(is._4),
          m.get(is._5),
          m.get(is._6),
          m.get(is._7),
          m.get(is._8),
          m.get(is._9),
          m.get(is._10),
          m.get(is._11),
          m.get(is._12)
        )
      }
  }

  implicit class Tuple13BatchSyntax[I](is: (I, I, I, I, I, I, I, I, I, I, I, I, I)) {
    def batchAll[F[_], A](implicit fetch: Fetch[F, I, A]) =
      fetch.batch(is.productIterator.asInstanceOf[Iterator[I]].toSet)
    def batchTupled[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]) = fetch
      .batch(
        Set(
          is._1,
          is._2,
          is._3,
          is._4,
          is._5,
          is._6,
          is._7,
          is._8,
          is._9,
          is._10,
          is._11,
          is._12,
          is._13
        )
      )
      .map { m =>
        (
          m.get(is._1),
          m.get(is._2),
          m.get(is._3),
          m.get(is._4),
          m.get(is._5),
          m.get(is._6),
          m.get(is._7),
          m.get(is._8),
          m.get(is._9),
          m.get(is._10),
          m.get(is._11),
          m.get(is._12),
          m.get(is._13)
        )
      }
  }

  implicit class Tuple14BatchSyntax[I](is: (I, I, I, I, I, I, I, I, I, I, I, I, I, I)) {
    def batchAll[F[_], A](implicit fetch: Fetch[F, I, A]) =
      fetch.batch(is.productIterator.asInstanceOf[Iterator[I]].toSet)
    def batchTupled[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]) = fetch
      .batch(
        Set(
          is._1,
          is._2,
          is._3,
          is._4,
          is._5,
          is._6,
          is._7,
          is._8,
          is._9,
          is._10,
          is._11,
          is._12,
          is._13,
          is._14
        )
      )
      .map { m =>
        (
          m.get(is._1),
          m.get(is._2),
          m.get(is._3),
          m.get(is._4),
          m.get(is._5),
          m.get(is._6),
          m.get(is._7),
          m.get(is._8),
          m.get(is._9),
          m.get(is._10),
          m.get(is._11),
          m.get(is._12),
          m.get(is._13),
          m.get(is._14)
        )
      }
  }

  implicit class Tuple15BatchSyntax[I](is: (I, I, I, I, I, I, I, I, I, I, I, I, I, I, I)) {
    def batchAll[F[_], A](implicit fetch: Fetch[F, I, A]) =
      fetch.batch(is.productIterator.asInstanceOf[Iterator[I]].toSet)
    def batchTupled[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]) = fetch
      .batch(
        Set(
          is._1,
          is._2,
          is._3,
          is._4,
          is._5,
          is._6,
          is._7,
          is._8,
          is._9,
          is._10,
          is._11,
          is._12,
          is._13,
          is._14,
          is._15
        )
      )
      .map { m =>
        (
          m.get(is._1),
          m.get(is._2),
          m.get(is._3),
          m.get(is._4),
          m.get(is._5),
          m.get(is._6),
          m.get(is._7),
          m.get(is._8),
          m.get(is._9),
          m.get(is._10),
          m.get(is._11),
          m.get(is._12),
          m.get(is._13),
          m.get(is._14),
          m.get(is._15)
        )
      }
  }

  implicit class Tuple16BatchSyntax[I](is: (I, I, I, I, I, I, I, I, I, I, I, I, I, I, I, I)) {
    def batchAll[F[_], A](implicit fetch: Fetch[F, I, A]) =
      fetch.batch(is.productIterator.asInstanceOf[Iterator[I]].toSet)
    def batchTupled[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]) = fetch
      .batch(
        Set(
          is._1,
          is._2,
          is._3,
          is._4,
          is._5,
          is._6,
          is._7,
          is._8,
          is._9,
          is._10,
          is._11,
          is._12,
          is._13,
          is._14,
          is._15,
          is._16
        )
      )
      .map { m =>
        (
          m.get(is._1),
          m.get(is._2),
          m.get(is._3),
          m.get(is._4),
          m.get(is._5),
          m.get(is._6),
          m.get(is._7),
          m.get(is._8),
          m.get(is._9),
          m.get(is._10),
          m.get(is._11),
          m.get(is._12),
          m.get(is._13),
          m.get(is._14),
          m.get(is._15),
          m.get(is._16)
        )
      }
  }

  implicit class Tuple17BatchSyntax[I](is: (I, I, I, I, I, I, I, I, I, I, I, I, I, I, I, I, I)) {
    def batchAll[F[_], A](implicit fetch: Fetch[F, I, A]) =
      fetch.batch(is.productIterator.asInstanceOf[Iterator[I]].toSet)
    def batchTupled[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]) = fetch
      .batch(
        Set(
          is._1,
          is._2,
          is._3,
          is._4,
          is._5,
          is._6,
          is._7,
          is._8,
          is._9,
          is._10,
          is._11,
          is._12,
          is._13,
          is._14,
          is._15,
          is._16,
          is._17
        )
      )
      .map { m =>
        (
          m.get(is._1),
          m.get(is._2),
          m.get(is._3),
          m.get(is._4),
          m.get(is._5),
          m.get(is._6),
          m.get(is._7),
          m.get(is._8),
          m.get(is._9),
          m.get(is._10),
          m.get(is._11),
          m.get(is._12),
          m.get(is._13),
          m.get(is._14),
          m.get(is._15),
          m.get(is._16),
          m.get(is._17)
        )
      }
  }

  implicit class Tuple18BatchSyntax[I](is: (I, I, I, I, I, I, I, I, I, I, I, I, I, I, I, I, I, I)) {
    def batchAll[F[_], A](implicit fetch: Fetch[F, I, A]) =
      fetch.batch(is.productIterator.asInstanceOf[Iterator[I]].toSet)
    def batchTupled[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]) = fetch
      .batch(
        Set(
          is._1,
          is._2,
          is._3,
          is._4,
          is._5,
          is._6,
          is._7,
          is._8,
          is._9,
          is._10,
          is._11,
          is._12,
          is._13,
          is._14,
          is._15,
          is._16,
          is._17,
          is._18
        )
      )
      .map { m =>
        (
          m.get(is._1),
          m.get(is._2),
          m.get(is._3),
          m.get(is._4),
          m.get(is._5),
          m.get(is._6),
          m.get(is._7),
          m.get(is._8),
          m.get(is._9),
          m.get(is._10),
          m.get(is._11),
          m.get(is._12),
          m.get(is._13),
          m.get(is._14),
          m.get(is._15),
          m.get(is._16),
          m.get(is._17),
          m.get(is._18)
        )
      }
  }

  implicit class Tuple19BatchSyntax[I](
      is: (I, I, I, I, I, I, I, I, I, I, I, I, I, I, I, I, I, I, I)
  ) {
    def batchAll[F[_], A](implicit fetch: Fetch[F, I, A]) =
      fetch.batch(is.productIterator.asInstanceOf[Iterator[I]].toSet)
    def batchTupled[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]) = fetch
      .batch(
        Set(
          is._1,
          is._2,
          is._3,
          is._4,
          is._5,
          is._6,
          is._7,
          is._8,
          is._9,
          is._10,
          is._11,
          is._12,
          is._13,
          is._14,
          is._15,
          is._16,
          is._17,
          is._18,
          is._19
        )
      )
      .map { m =>
        (
          m.get(is._1),
          m.get(is._2),
          m.get(is._3),
          m.get(is._4),
          m.get(is._5),
          m.get(is._6),
          m.get(is._7),
          m.get(is._8),
          m.get(is._9),
          m.get(is._10),
          m.get(is._11),
          m.get(is._12),
          m.get(is._13),
          m.get(is._14),
          m.get(is._15),
          m.get(is._16),
          m.get(is._17),
          m.get(is._18),
          m.get(is._19)
        )
      }
  }

  implicit class Tuple20BatchSyntax[I](
      is: (I, I, I, I, I, I, I, I, I, I, I, I, I, I, I, I, I, I, I, I)
  ) {
    def batchAll[F[_], A](implicit fetch: Fetch[F, I, A]) =
      fetch.batch(is.productIterator.asInstanceOf[Iterator[I]].toSet)
    def batchTupled[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]) = fetch
      .batch(
        Set(
          is._1,
          is._2,
          is._3,
          is._4,
          is._5,
          is._6,
          is._7,
          is._8,
          is._9,
          is._10,
          is._11,
          is._12,
          is._13,
          is._14,
          is._15,
          is._16,
          is._17,
          is._18,
          is._19,
          is._20
        )
      )
      .map { m =>
        (
          m.get(is._1),
          m.get(is._2),
          m.get(is._3),
          m.get(is._4),
          m.get(is._5),
          m.get(is._6),
          m.get(is._7),
          m.get(is._8),
          m.get(is._9),
          m.get(is._10),
          m.get(is._11),
          m.get(is._12),
          m.get(is._13),
          m.get(is._14),
          m.get(is._15),
          m.get(is._16),
          m.get(is._17),
          m.get(is._18),
          m.get(is._19),
          m.get(is._20)
        )
      }
  }

  implicit class Tuple21BatchSyntax[I](
      is: (I, I, I, I, I, I, I, I, I, I, I, I, I, I, I, I, I, I, I, I, I)
  ) {
    def batchAll[F[_], A](implicit fetch: Fetch[F, I, A]) =
      fetch.batch(is.productIterator.asInstanceOf[Iterator[I]].toSet)
    def batchTupled[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]) = fetch
      .batch(
        Set(
          is._1,
          is._2,
          is._3,
          is._4,
          is._5,
          is._6,
          is._7,
          is._8,
          is._9,
          is._10,
          is._11,
          is._12,
          is._13,
          is._14,
          is._15,
          is._16,
          is._17,
          is._18,
          is._19,
          is._20,
          is._21
        )
      )
      .map { m =>
        (
          m.get(is._1),
          m.get(is._2),
          m.get(is._3),
          m.get(is._4),
          m.get(is._5),
          m.get(is._6),
          m.get(is._7),
          m.get(is._8),
          m.get(is._9),
          m.get(is._10),
          m.get(is._11),
          m.get(is._12),
          m.get(is._13),
          m.get(is._14),
          m.get(is._15),
          m.get(is._16),
          m.get(is._17),
          m.get(is._18),
          m.get(is._19),
          m.get(is._20),
          m.get(is._21)
        )
      }
  }

  implicit class Tuple22BatchSyntax[I](
      is: (I, I, I, I, I, I, I, I, I, I, I, I, I, I, I, I, I, I, I, I, I, I)
  ) {
    def batchAll[F[_], A](implicit fetch: Fetch[F, I, A]) =
      fetch.batch(is.productIterator.asInstanceOf[Iterator[I]].toSet)
    def batchTupled[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]) = fetch
      .batch(
        Set(
          is._1,
          is._2,
          is._3,
          is._4,
          is._5,
          is._6,
          is._7,
          is._8,
          is._9,
          is._10,
          is._11,
          is._12,
          is._13,
          is._14,
          is._15,
          is._16,
          is._17,
          is._18,
          is._19,
          is._20,
          is._21,
          is._22
        )
      )
      .map { m =>
        (
          m.get(is._1),
          m.get(is._2),
          m.get(is._3),
          m.get(is._4),
          m.get(is._5),
          m.get(is._6),
          m.get(is._7),
          m.get(is._8),
          m.get(is._9),
          m.get(is._10),
          m.get(is._11),
          m.get(is._12),
          m.get(is._13),
          m.get(is._14),
          m.get(is._15),
          m.get(is._16),
          m.get(is._17),
          m.get(is._18),
          m.get(is._19),
          m.get(is._20),
          m.get(is._21),
          m.get(is._22)
        )
      }
  }
}
