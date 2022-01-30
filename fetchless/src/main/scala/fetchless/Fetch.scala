package fetchless

import cats.syntax.all._
import cats.{Applicative, Functor, Traverse}
import cats.effect.Clock
import scala.concurrent.duration.FiniteDuration
import cats.Parallel
import cats.CommutativeApplicative

/**
 * The ability to fetch values `A` given an ID `I`. Represents a data source such as a database,
 * cache, or other possibly remote resource.
 */
trait Fetch[F[_], I, A] {
  def single(i: I): F[Option[A]]
  def batch(iSet: Set[I]): F[Map[I, A]]
  def batch[G[_]: Traverse](is: G[I]): F[Map[I, A]] = batch(is.toIterable.toSet)
}

object Fetch {

  /**
   * Allows creating a `Fetch` instance for some data source that does not allow for batching.
   * Batches are implemented as sequenced single fetches with no parallelism.
   */
  def singleSequenced[F[_]: Applicative, I, A](f: I => F[Option[A]]): Fetch[F, I, A] =
    new Fetch[F, I, A] {
      def single(i: I) = f(i)
      def batch(iSet: Set[I]) = iSet.toList
        .traverse { i =>
          f(i).map(_.tupleLeft(i))
        }
        .map(_.flattenOption.toMap)
    }

  /**
   * Allows creating a `Fetch` instance for some data source that does not allow for batching.
   * Batches are implemented as sequenced single fetches with no parallelism.
   */
  def singleParallel[F[_]: Applicative: Parallel, I, A](
      f: I => F[Option[A]]
  ): Fetch[F, I, A] =
    new Fetch[F, I, A] {
      def single(i: I) = f(i)
      def batch(iSet: Set[I]) = iSet.toList
        .parTraverse { i =>
          f(i).map(_.tupleLeft(i))
        }
        .map(_.flattenOption.toMap)
    }

  /** A `Fetch` instance that has separate single and batch fetch implementations. */
  def batchable[F[_], I, A](
      singleFunction: I => F[Option[A]]
  )(batchFunction: Set[I] => F[Map[I, A]]): Fetch[F, I, A] = new Fetch[F, I, A] {
    def single(i: I)        = singleFunction(i)
    def batch(iSet: Set[I]) = batchFunction(iSet)
  }

  /**
   * A `Fetch` instanced that has only the ability to make batch requests. Single fetches are
   * implemented in terms of batches. Useful for cases where there is only one method of fetching
   * data from your source and it allows for batching.
   */
  def batchOnly[F[_]: Functor, I, A](batchFunction: Set[I] => F[Map[I, A]]): Fetch[F, I, A] =
    new Fetch[F, I, A] {
      def single(i: I): F[Option[A]]        = batch(Set(i)).map(_.get(i))
      def batch(iSet: Set[I]): F[Map[I, A]] = batchFunction(iSet)
    }

  /** A `Fetch` instance backed by a local map. Useful for testing, debugging, or other usages. */
  def const[F[_]: Applicative, I, A](map: Map[I, A]) = new Fetch[F, I, A] {
    def single(i: I): F[Option[A]]        = map.get(i).pure[F]
    def batch(iSet: Set[I]): F[Map[I, A]] = map.filter { case (i, _) => iSet.contains(i) }.pure[F]
  }
}
