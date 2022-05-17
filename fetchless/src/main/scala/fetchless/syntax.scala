package fetchless

import cats.{FlatMap, Functor, Traverse}
import cats.syntax.all._
import cats.Monad
import cats.data.Kleisli
import cats.Applicative
import cats.effect.Clock

object syntax {

  implicit class FDedupedRequestSyntax[F[_]: FlatMap, A](fdf: F[DedupedRequest[F, A]]) {
    def alsoFetch[I, B](i: I)(implicit fetch: Fetch[F, I, B]): F[DedupedRequest[F, Option[B]]] =
      fdf.flatMap { df =>
        df.alsoFetch(i)
      }
    def alsoFetch[I, B](fetch: Fetch[F, I, B])(i: I): F[DedupedRequest[F, Option[B]]] =
      fdf.flatMap { df =>
        df.alsoFetch(fetch)(i)
      }
    def alsoBatch[I, B](
        is: Set[I]
    )(implicit fetch: Fetch[F, I, B]): F[DedupedRequest[F, Map[I, B]]] = fdf.flatMap { df =>
      df.alsoBatch(is)
    }
    def alsoBatch[I, B](fetch: Fetch[F, I, B])(is: Set[I]): F[DedupedRequest[F, Map[I, B]]] =
      fdf.flatMap { df =>
        df.alsoBatch(fetch)(is)
      }
    def alsoBatchAll[I, B](implicit fetch: AllFetch[F, I, B]): F[DedupedRequest[F, Map[I, B]]] =
      fdf.flatMap { df =>
        df.alsoBatchAll
      }
  }

  implicit class SingleSyntax[I](i: I) {

    /**
     * Fetches a single result. Does not try to auto-batch requests.
     */
    def fetch[F[_], A](implicit fetch: Fetch[F, I, A]): F[Option[A]] = fetch.single(i)
    def fetchDedupe[F[_], A](implicit fetch: Fetch[F, I, A]): F[DedupedRequest[F, Option[A]]] =
      fetch.singleDedupe(i)
    def fetchLazy[F[_]: Applicative, A](implicit fetch: Fetch[F, I, A]): LazyRequest[F, Option[A]] =
      fetch.singleLazy(i)
  }

  implicit class EffectfulSyntax[F[_]: FlatMap, I](fi: F[I]) {

    /**
     * Fetches a single result. Does not try to auto-batch requests.
     */
    def fetch[A](implicit fetch: Fetch[F, I, A]): F[Option[A]] = fi.flatMap(fetch.single(_))
    def fetchDedupe[A](implicit fetch: Fetch[F, I, A]): F[DedupedRequest[F, Option[A]]] =
      fi.flatMap(fetch.singleDedupe(_))
  }

  implicit class EffectfulSyntaxMonad[F[_]: Monad, I](fi: F[I]) {
    def fetchLazy[A](id: Any)(implicit fetch: Fetch[F, I, A]): LazyRequest[F, Option[A]] =
      LazyRequest.liftF(fi, id).flatMap(i => fetch.singleLazy(i))
    def fetchLazyTimed[A](id: Any, timer: FetchTimer[F])(implicit
        fetch: Fetch[F, I, A]
    ): LazyRequest[F, Option[A]] =
      LazyRequest.liftTimedF(fi, id, timer).flatMap(i => fetch.singleLazy(i))
    def fetchLazyTimed[A](id: Any)(implicit
        fetch: Fetch[F, I, A],
        clock: Clock[F]
    ): LazyRequest[F, Option[A]] =
      LazyRequest.liftTimedF(fi, id, FetchTimer.clock[F]).flatMap(i => fetch.singleLazy(i))
  }

  implicit class TraverseBatchSyntax[G[_]: Traverse, I](is: G[I]) {

    /**
     * Fetches all results in the current collection. Will try to batch requests if your Fetch
     * instance supports it.
     */
    def fetchAll[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]): F[G[Option[A]]] =
      fetchAllMap[F, A].map { m =>
        is.map(i => m.get(i))
      }
    def fetchAll[F[_]: Functor, A](fetch: Fetch[F, I, A]): F[G[Option[A]]] =
      fetchAllMap[F, A](fetch).map { m =>
        is.map(i => m.get(i))
      }
    def fetchAllMap[F[_], A](implicit fetch: Fetch[F, I, A]): F[Map[I, A]] =
      fetch.batch(is)
    def fetchAllDedupe[F[_]: Functor, A](implicit
        fetch: Fetch[F, I, A]
    ): F[DedupedRequest[F, G[Option[A]]]] = fetchAllDedupeMap[F, A].map { d =>
      d.map { m =>
        is.map(i => m.get(i))
      }
    }
    def fetchAllDedupe[F[_]: Functor, A](
        fetch: Fetch[F, I, A]
    ): F[DedupedRequest[F, G[Option[A]]]] = fetchAllDedupeMap[F, A](fetch).map { d =>
      d.map { m =>
        is.map(i => m.get(i))
      }
    }
    def fetchAllDedupeMap[F[_], A](implicit
        fetch: Fetch[F, I, A]
    ): F[DedupedRequest[F, Map[I, A]]] =
      fetch.batchDedupe(is)
    def fetchAllLazy[F[_]: Applicative, A](implicit
        fetch: Fetch[F, I, A]
    ): LazyRequest[F, G[Option[A]]] = fetchAllLazyMap[F, A].map { m =>
      is.map(i => m.get(i))
    }
    def fetchAllLazy[F[_]: Applicative, A](
        fetch: Fetch[F, I, A]
    ): LazyRequest[F, G[Option[A]]] = fetchAllLazyMap[F, A](implicitly, fetch).map { m =>
      is.map(i => m.get(i))
    }
    def fetchAllLazyMap[F[_]: Applicative, A](implicit
        fetch: Fetch[F, I, A]
    ): LazyRequest[F, Map[I, A]] =
      fetch.batchLazy(is)
  }

  implicit class Tuple2BatchSyntax[I](is: (I, I)) {

    /**
     * Fetches all results in the current tuple, as a map. Will try to batch requests if your Fetch
     * instance supports it.
     */
    def fetchAllMap[F[_], A](implicit fetch: Fetch[F, I, A]): F[Map[I, A]] =
      fetch.batch(Set(is._1, is._2))

    def fetchAllDedupeMap[F[_], A](implicit
        fetch: Fetch[F, I, A]
    ): F[DedupedRequest[F, Map[I, A]]] =
      fetch.batchDedupe(Set(is._1, is._2))

    def fetchAllLazyMap[F[_]: Applicative, A](implicit
        fetch: Fetch[F, I, A]
    ): LazyRequest[F, Map[I, A]] =
      fetch.batchLazy(Set(is._1, is._2))

    /**
     * Fetches all results in the current tuple, retaining the tuple structure. Will try to batch
     * requests if your `Fetch` instance supports it.
     */
    def fetchTupled[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]): F[(Option[A], Option[A])] =
      fetch.batch(Set(is._1, is._2)).map { m =>
        (m.get(is._1), m.get(is._2))
      }
    def fetchTupled[F[_]: Functor, A](fetch: Fetch[F, I, A]): F[(Option[A], Option[A])] =
      fetch.batch(Set(is._1, is._2)).map { m =>
        (m.get(is._1), m.get(is._2))
      }
    def fetchTupledDedupe[F[_]: Monad, A](implicit
        fetch: Fetch[F, I, A]
    ): F[DedupedRequest[F, (Option[A], Option[A])]] =
      fetch.batchDedupe(Set(is._1, is._2)).map { df =>
        df.map(m => (m.get(is._1), m.get(is._2)))
      }
    def fetchTupledDedupe[F[_]: Monad, A](
        fetch: Fetch[F, I, A]
    ): F[DedupedRequest[F, (Option[A], Option[A])]] =
      fetch.batchDedupe(Set(is._1, is._2)).map { df =>
        df.map(m => (m.get(is._1), m.get(is._2)))
      }
    def fetchTupledLazy[F[_]: Monad, A](implicit
        fetch: Fetch[F, I, A]
    ): LazyRequest[F, (Option[A], Option[A])] =
      fetch.batchLazy(Set(is._1, is._2)).map { m =>
        (m.get(is._1), m.get(is._2))
      }
    def fetchTupledLazy[F[_]: Monad, A](
        fetch: Fetch[F, I, A]
    ): LazyRequest[F, (Option[A], Option[A])] =
      fetch.batchLazy(Set(is._1, is._2)).map { m =>
        (m.get(is._1), m.get(is._2))
      }
  }

  implicit class Tuple3BatchSyntax[I](is: (I, I, I)) {

    /**
     * Fetches all results in the current tuple, as a map. Will try to batch requests if your Fetch
     * instance supports it.
     */
    def fetchAllMap[F[_], A](implicit fetch: Fetch[F, I, A]): F[Map[I, A]] =
      fetch.batch(Set(is._1, is._2, is._3))

    def fetchAllDedupeMap[F[_], A](implicit
        fetch: Fetch[F, I, A]
    ): F[DedupedRequest[F, Map[I, A]]] =
      fetch.batchDedupe(Set(is._1, is._2, is._3))

    def fetchAllLazyMap[F[_]: Applicative, A](implicit
        fetch: Fetch[F, I, A]
    ): LazyRequest[F, Map[I, A]] =
      fetch.batchLazy(Set(is._1, is._2, is._3))

    /**
     * Fetches all results in the current tuple, retaining the tuple structure. Will try to batch
     * requests if your `Fetch` instance supports it.
     */
    def fetchTupled[F[_]: Functor, A](implicit
        fetch: Fetch[F, I, A]
    ): F[(Option[A], Option[A], Option[A])] =
      fetch.batch(Set(is._1, is._2, is._3)).map { m =>
        (m.get(is._1), m.get(is._2), m.get(is._3))
      }
    def fetchTupled[F[_]: Functor, A](fetch: Fetch[F, I, A]): F[(Option[A], Option[A], Option[A])] =
      fetch.batch(Set(is._1, is._2, is._3)).map { m =>
        (m.get(is._1), m.get(is._2), m.get(is._3))
      }
    def fetchTupledDedupe[F[_]: Monad, A](implicit
        fetch: Fetch[F, I, A]
    ): F[DedupedRequest[F, (Option[A], Option[A], Option[A])]] =
      fetch.batchDedupe(Set(is._1, is._2, is._3)).map { df =>
        df.map(m => (m.get(is._1), m.get(is._2), m.get(is._3)))
      }
    def fetchTupledDedupe[F[_]: Monad, A](
        fetch: Fetch[F, I, A]
    ): F[DedupedRequest[F, (Option[A], Option[A], Option[A])]] =
      fetch.batchDedupe(Set(is._1, is._2, is._3)).map { df =>
        df.map(m => (m.get(is._1), m.get(is._2), m.get(is._3)))
      }
    def fetchTupledLazy[F[_]: Monad, A](implicit
        fetch: Fetch[F, I, A]
    ): LazyRequest[F, (Option[A], Option[A], Option[A])] =
      fetch.batchLazy(Set(is._1, is._2, is._3)).map { m =>
        (m.get(is._1), m.get(is._2), m.get(is._3))
      }
    def fetchTupledLazy[F[_]: Monad, A](
        fetch: Fetch[F, I, A]
    ): LazyRequest[F, (Option[A], Option[A], Option[A])] =
      fetch.batchLazy(Set(is._1, is._2, is._3)).map { m =>
        (m.get(is._1), m.get(is._2), m.get(is._3))
      }
  }

  implicit class Tuple4BatchSyntax[I](is: (I, I, I, I)) {

    /**
     * Fetches all results in the current tuple, as a map. Will try to batch requests if your Fetch
     * instance supports it.
     */
    def fetchAllMap[F[_], A](implicit fetch: Fetch[F, I, A]): F[Map[I, A]] =
      fetch.batch(Set(is._1, is._2, is._3, is._4))

    def fetchAllDedupeMap[F[_], A](implicit
        fetch: Fetch[F, I, A]
    ): F[DedupedRequest[F, Map[I, A]]] =
      fetch.batchDedupe(Set(is._1, is._2, is._3, is._4))

    def fetchAllLazyMap[F[_]: Applicative, A](implicit
        fetch: Fetch[F, I, A]
    ): LazyRequest[F, Map[I, A]] =
      fetch.batchLazy(Set(is._1, is._2, is._3, is._4))

    /**
     * Fetches all results in the current tuple, retaining the tuple structure. Will try to batch
     * requests if your `Fetch` instance supports it.
     */
    def fetchTupled[F[_]: Functor, A](implicit
        fetch: Fetch[F, I, A]
    ): F[(Option[A], Option[A], Option[A], Option[A])] =
      fetch.batch(Set(is._1, is._2, is._3, is._4)).map { m =>
        (m.get(is._1), m.get(is._2), m.get(is._3), m.get(is._4))
      }
    def fetchTupled[F[_]: Functor, A](
        fetch: Fetch[F, I, A]
    ): F[(Option[A], Option[A], Option[A], Option[A])] =
      fetch.batch(Set(is._1, is._2, is._3, is._4)).map { m =>
        (m.get(is._1), m.get(is._2), m.get(is._3), m.get(is._4))
      }
    def fetchTupledDedupe[F[_]: Functor, A](implicit
        fetch: Fetch[F, I, A]
    ): F[DedupedRequest[F, (Option[A], Option[A], Option[A], Option[A])]] =
      fetch.batchDedupe(Set(is._1, is._2, is._3, is._4)).map { df =>
        df.map { m =>
          (m.get(is._1), m.get(is._2), m.get(is._3), m.get(is._4))
        }
      }
    def fetchTupledDedupe[F[_]: Functor, A](
        fetch: Fetch[F, I, A]
    ): F[DedupedRequest[F, (Option[A], Option[A], Option[A], Option[A])]] =
      fetch.batchDedupe(Set(is._1, is._2, is._3, is._4)).map { df =>
        df.map { m =>
          (m.get(is._1), m.get(is._2), m.get(is._3), m.get(is._4))
        }
      }
    def fetchTupledLazy[F[_]: Monad, A](implicit
        fetch: Fetch[F, I, A]
    ): LazyRequest[F, (Option[A], Option[A], Option[A], Option[A])] =
      fetch.batchLazy(Set(is._1, is._2, is._3, is._4)).map { m =>
        (m.get(is._1), m.get(is._2), m.get(is._3), m.get(is._4))
      }
    def fetchTupledLazy[F[_]: Monad, A](
        fetch: Fetch[F, I, A]
    ): LazyRequest[F, (Option[A], Option[A], Option[A], Option[A])] =
      fetch.batchLazy(Set(is._1, is._2, is._3, is._4)).map { m =>
        (m.get(is._1), m.get(is._2), m.get(is._3), m.get(is._4))
      }
  }

  implicit class Tuple5BatchSyntax[I](is: (I, I, I, I, I)) {

    /**
     * Fetches all results in the current tuple, as a map. Will try to batch requests if your Fetch
     * instance supports it.
     */
    def fetchAllMap[F[_], A](implicit fetch: Fetch[F, I, A]): F[Map[I, A]] =
      fetch.batch(Set(is._1, is._2, is._3, is._4, is._5))

    def fetchAllDedupeMap[F[_], A](implicit
        fetch: Fetch[F, I, A]
    ): F[DedupedRequest[F, Map[I, A]]] =
      fetch.batchDedupe(Set(is._1, is._2, is._3, is._4, is._5))

    def fetchAllLazyMap[F[_]: Applicative, A](implicit
        fetch: Fetch[F, I, A]
    ): LazyRequest[F, Map[I, A]] =
      fetch.batchLazy(Set(is._1, is._2, is._3, is._4, is._5))

    /**
     * Fetches all results in the current tuple, retaining the tuple structure. Will try to batch
     * requests if your `Fetch` instance supports it.
     */
    def fetchTupled[F[_]: Functor, A](implicit
        fetch: Fetch[F, I, A]
    ): F[(Option[A], Option[A], Option[A], Option[A], Option[A])] =
      fetch.batch(Set(is._1, is._2, is._3, is._4, is._5)).map { m =>
        (m.get(is._1), m.get(is._2), m.get(is._3), m.get(is._4), m.get(is._5))
      }

    def fetchTupled[F[_]: Functor, A](
        fetch: Fetch[F, I, A]
    ): F[(Option[A], Option[A], Option[A], Option[A], Option[A])] =
      fetch.batch(Set(is._1, is._2, is._3, is._4, is._5)).map { m =>
        (m.get(is._1), m.get(is._2), m.get(is._3), m.get(is._4), m.get(is._5))
      }
    def fetchTupledDedupe[F[_]: Functor, A](implicit
        fetch: Fetch[F, I, A]
    ): F[DedupedRequest[F, (Option[A], Option[A], Option[A], Option[A], Option[A])]] =
      fetch.batchDedupe(Set(is._1, is._2, is._3, is._4, is._5)).map { df =>
        df.map { m =>
          (m.get(is._1), m.get(is._2), m.get(is._3), m.get(is._4), m.get(is._5))
        }
      }
    def fetchTupledDedupe[F[_]: Functor, A](
        fetch: Fetch[F, I, A]
    ): F[DedupedRequest[F, (Option[A], Option[A], Option[A], Option[A], Option[A])]] =
      fetch.batchDedupe(Set(is._1, is._2, is._3, is._4, is._5)).map { df =>
        df.map { m =>
          (m.get(is._1), m.get(is._2), m.get(is._3), m.get(is._4), m.get(is._5))
        }
      }
    def fetchTupledLazy[F[_]: Monad, A](implicit
        fetch: Fetch[F, I, A]
    ): LazyRequest[F, (Option[A], Option[A], Option[A], Option[A], Option[A])] =
      fetch.batchLazy(Set(is._1, is._2, is._3, is._4, is._5)).map { m =>
        (m.get(is._1), m.get(is._2), m.get(is._3), m.get(is._4), m.get(is._5))
      }
    def fetchTupledLazy[F[_]: Monad, A](
        fetch: Fetch[F, I, A]
    ): LazyRequest[F, (Option[A], Option[A], Option[A], Option[A], Option[A])] =
      fetch.batchLazy(Set(is._1, is._2, is._3, is._4, is._5)).map { m =>
        (m.get(is._1), m.get(is._2), m.get(is._3), m.get(is._4), m.get(is._5))
      }
  }

  implicit class Tuple6BatchSyntax[I](is: (I, I, I, I, I, I)) {

    /**
     * Fetches all results in the current tuple, as a map. Will try to batch requests if your Fetch
     * instance supports it.
     */
    def fetchAllMap[F[_], A](implicit fetch: Fetch[F, I, A]): F[Map[I, A]] =
      fetch.batch(Set(is._1, is._2, is._3, is._4, is._5, is._6))

    def fetchAllDedupeMap[F[_], A](implicit
        fetch: Fetch[F, I, A]
    ): F[DedupedRequest[F, Map[I, A]]] =
      fetch.batchDedupe(Set(is._1, is._2, is._3, is._4, is._5, is._6))

    def fetchAllLazyMap[F[_]: Applicative, A](implicit
        fetch: Fetch[F, I, A]
    ): LazyRequest[F, Map[I, A]] =
      fetch.batchLazy(Set(is._1, is._2, is._3, is._4, is._5, is._6))

    /**
     * Fetches all results in the current tuple, retaining the tuple structure. Will try to batch
     * requests if your `Fetch` instance supports it.
     */
    def fetchTupled[F[_]: Functor, A](implicit
        fetch: Fetch[F, I, A]
    ): F[(Option[A], Option[A], Option[A], Option[A], Option[A], Option[A])] =
      fetch.batch(Set(is._1, is._2, is._3, is._4, is._5, is._6)).map { m =>
        (m.get(is._1), m.get(is._2), m.get(is._3), m.get(is._4), m.get(is._5), m.get(is._6))
      }

    def fetchTupled[F[_]: Functor, A](
        fetch: Fetch[F, I, A]
    ): F[(Option[A], Option[A], Option[A], Option[A], Option[A], Option[A])] =
      fetch.batch(Set(is._1, is._2, is._3, is._4, is._5, is._6)).map { m =>
        (m.get(is._1), m.get(is._2), m.get(is._3), m.get(is._4), m.get(is._5), m.get(is._6))
      }
    def fetchTupledDedupe[F[_]: Functor, A](implicit
        fetch: Fetch[F, I, A]
    ): F[DedupedRequest[F, (Option[A], Option[A], Option[A], Option[A], Option[A], Option[A])]] =
      fetch.batchDedupe(Set(is._1, is._2, is._3, is._4, is._5, is._6)).map { df =>
        df.map { m =>
          (m.get(is._1), m.get(is._2), m.get(is._3), m.get(is._4), m.get(is._5), m.get(is._6))
        }
      }
    def fetchTupledDedupe[F[_]: Functor, A](
        fetch: Fetch[F, I, A]
    ): F[DedupedRequest[F, (Option[A], Option[A], Option[A], Option[A], Option[A], Option[A])]] =
      fetch.batchDedupe(Set(is._1, is._2, is._3, is._4, is._5, is._6)).map { df =>
        df.map { m =>
          (m.get(is._1), m.get(is._2), m.get(is._3), m.get(is._4), m.get(is._5), m.get(is._6))
        }
      }
    def fetchTupledLazy[F[_]: Monad, A](implicit
        fetch: Fetch[F, I, A]
    ): LazyRequest[F, (Option[A], Option[A], Option[A], Option[A], Option[A], Option[A])] =
      fetch.batchLazy(Set(is._1, is._2, is._3, is._4, is._5, is._6)).map { m =>
        (m.get(is._1), m.get(is._2), m.get(is._3), m.get(is._4), m.get(is._5), m.get(is._6))
      }
    def fetchTupledLazy[F[_]: Monad, A](
        fetch: Fetch[F, I, A]
    ): LazyRequest[F, (Option[A], Option[A], Option[A], Option[A], Option[A], Option[A])] =
      fetch.batchLazy(Set(is._1, is._2, is._3, is._4, is._5, is._6)).map { m =>
        (m.get(is._1), m.get(is._2), m.get(is._3), m.get(is._4), m.get(is._5), m.get(is._6))
      }
  }

  implicit class Tuple7BatchSyntax[I](is: (I, I, I, I, I, I, I)) {

    /**
     * Fetches all results in the current tuple, as a map. Will try to batch requests if your Fetch
     * instance supports it.
     */
    def fetchAllMap[F[_], A](implicit fetch: Fetch[F, I, A]): F[Map[I, A]] =
      fetch.batch(Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7))

    def fetchAllDedupeMap[F[_], A](implicit
        fetch: Fetch[F, I, A]
    ): F[DedupedRequest[F, Map[I, A]]] =
      fetch.batchDedupe(Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7))

    def fetchAllLazyMap[F[_]: Applicative, A](implicit
        fetch: Fetch[F, I, A]
    ): LazyRequest[F, Map[I, A]] =
      fetch.batchLazy(Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7))

    /**
     * Fetches all results in the current tuple, retaining the tuple structure. Will try to batch
     * requests if your `Fetch` instance supports it.
     */
    def fetchTupled[F[_]: Functor, A](implicit
        fetch: Fetch[F, I, A]
    ): F[(Option[A], Option[A], Option[A], Option[A], Option[A], Option[A], Option[A])] =
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

    def fetchTupled[F[_]: Functor, A](
        fetch: Fetch[F, I, A]
    ): F[(Option[A], Option[A], Option[A], Option[A], Option[A], Option[A], Option[A])] =
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
    def fetchTupledDedupe[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]): F[
      DedupedRequest[
        F,
        (Option[A], Option[A], Option[A], Option[A], Option[A], Option[A], Option[A])
      ]
    ] =
      fetch.batchDedupe(Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7)).map { df =>
        df.map { m =>
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
    def fetchTupledDedupe[F[_]: Functor, A](fetch: Fetch[F, I, A]): F[
      DedupedRequest[
        F,
        (Option[A], Option[A], Option[A], Option[A], Option[A], Option[A], Option[A])
      ]
    ] =
      fetch.batchDedupe(Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7)).map { df =>
        df.map { m =>
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
    def fetchTupledLazy[F[_]: Monad, A](implicit
        fetch: Fetch[F, I, A]
    ): LazyRequest[
      F,
      (Option[A], Option[A], Option[A], Option[A], Option[A], Option[A], Option[A])
    ] =
      fetch.batchLazy(Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7)).map { m =>
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
    def fetchTupledLazy[F[_]: Monad, A](
        fetch: Fetch[F, I, A]
    ): LazyRequest[
      F,
      (Option[A], Option[A], Option[A], Option[A], Option[A], Option[A], Option[A])
    ] =
      fetch.batchLazy(Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7)).map { m =>
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

    /**
     * Fetches all results in the current tuple, as a map. Will try to batch requests if your Fetch
     * instance supports it.
     */
    def fetchAllMap[F[_], A](implicit fetch: Fetch[F, I, A]): F[Map[I, A]] =
      fetch.batch(Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7, is._8))

    def fetchAllDedupeMap[F[_], A](implicit
        fetch: Fetch[F, I, A]
    ): F[DedupedRequest[F, Map[I, A]]] =
      fetch.batchDedupe(Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7, is._8))

    def fetchAllLazyMap[F[_]: Applicative, A](implicit
        fetch: Fetch[F, I, A]
    ): LazyRequest[F, Map[I, A]] =
      fetch.batchLazy(Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7, is._8))

    /**
     * Fetches all results in the current tuple, retaining the tuple structure. Will try to batch
     * requests if your `Fetch` instance supports it.
     */
    def fetchTupled[F[_]: Functor, A](implicit
        fetch: Fetch[F, I, A]
    ): F[(Option[A], Option[A], Option[A], Option[A], Option[A], Option[A], Option[A], Option[A])] =
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

    def fetchTupled[F[_]: Functor, A](
        fetch: Fetch[F, I, A]
    ): F[(Option[A], Option[A], Option[A], Option[A], Option[A], Option[A], Option[A], Option[A])] =
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
    def fetchTupledDedupe[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]): F[
      DedupedRequest[
        F,
        (Option[A], Option[A], Option[A], Option[A], Option[A], Option[A], Option[A], Option[A])
      ]
    ] =
      fetch.batchDedupe(Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7, is._8)).map { df =>
        df.map { m =>
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
    def fetchTupledDedupe[F[_]: Functor, A](fetch: Fetch[F, I, A]): F[
      DedupedRequest[
        F,
        (Option[A], Option[A], Option[A], Option[A], Option[A], Option[A], Option[A], Option[A])
      ]
    ] =
      fetch.batchDedupe(Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7, is._8)).map { df =>
        df.map { m =>
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
    def fetchTupledLazy[F[_]: Monad, A](implicit fetch: Fetch[F, I, A]): LazyRequest[
      F,
      (Option[A], Option[A], Option[A], Option[A], Option[A], Option[A], Option[A], Option[A])
    ] =
      fetch.batchLazy(Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7, is._8)).map { m =>
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
    def fetchTupledLazy[F[_]: Monad, A](fetch: Fetch[F, I, A]): LazyRequest[
      F,
      (Option[A], Option[A], Option[A], Option[A], Option[A], Option[A], Option[A], Option[A])
    ] =
      fetch.batchLazy(Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7, is._8)).map { m =>
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

    /**
     * Fetches all results in the current tuple, as a map. Will try to batch requests if your Fetch
     * instance supports it.
     */
    def fetchAllMap[F[_], A](implicit fetch: Fetch[F, I, A]): F[Map[I, A]] =
      fetch.batch(Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7, is._8, is._9))

    def fetchAllDedupeMap[F[_], A](implicit
        fetch: Fetch[F, I, A]
    ): F[DedupedRequest[F, Map[I, A]]] =
      fetch.batchDedupe(Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7, is._8, is._9))

    def fetchAllLazyMap[F[_]: Applicative, A](implicit
        fetch: Fetch[F, I, A]
    ): LazyRequest[F, Map[I, A]] =
      fetch.batchLazy(Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7, is._8, is._9))

    /**
     * Fetches all results in the current tuple, retaining the tuple structure. Will try to batch
     * requests if your `Fetch` instance supports it.
     */
    def fetchTupled[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]): F[
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] =
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

    def fetchTupled[F[_]: Functor, A](fetch: Fetch[F, I, A]): F[
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] =
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
    def fetchTupledDedupe[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]): F[
      DedupedRequest[
        F,
        (
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A]
        )
      ]
    ] =
      fetch.batchDedupe(Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7, is._8, is._9)).map {
        df =>
          df.map { m =>
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
    def fetchTupledDedupe[F[_]: Functor, A](fetch: Fetch[F, I, A]): F[
      DedupedRequest[
        F,
        (
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A]
        )
      ]
    ] =
      fetch.batchDedupe(Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7, is._8, is._9)).map {
        df =>
          df.map { m =>
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
    def fetchTupledLazy[F[_]: Monad, A](implicit fetch: Fetch[F, I, A]): LazyRequest[
      F,
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] =
      fetch.batchLazy(Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7, is._8, is._9)).map { m =>
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
    def fetchTupledLazy[F[_]: Monad, A](fetch: Fetch[F, I, A]): LazyRequest[
      F,
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] =
      fetch.batchLazy(Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7, is._8, is._9)).map { m =>
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

    /**
     * Fetches all results in the current tuple, as a map. Will try to batch requests if your Fetch
     * instance supports it.
     */
    def fetchAllMap[F[_], A](implicit fetch: Fetch[F, I, A]): F[Map[I, A]] =
      fetch.batch(Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7, is._8, is._9, is._10))

    def fetchAllDedupeMap[F[_], A](implicit
        fetch: Fetch[F, I, A]
    ): F[DedupedRequest[F, Map[I, A]]] =
      fetch.batchDedupe(Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7, is._8, is._9, is._10))

    def fetchAllLazyMap[F[_]: Applicative, A](implicit
        fetch: Fetch[F, I, A]
    ): LazyRequest[F, Map[I, A]] =
      fetch.batchLazy(Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7, is._8, is._9, is._10))

    /**
     * Fetches all results in the current tuple, retaining the tuple structure. Will try to batch
     * requests if your `Fetch` instance supports it.
     */
    def fetchTupled[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]): F[
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] =
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

    def fetchTupled[F[_]: Functor, A](fetch: Fetch[F, I, A]): F[
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] =
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
    def fetchTupledDedupe[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]): F[
      DedupedRequest[
        F,
        (
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A]
        )
      ]
    ] =
      fetch
        .batchDedupe(Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7, is._8, is._9, is._10))
        .map { df =>
          df.map { m =>
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
    def fetchTupledDedupe[F[_]: Functor, A](fetch: Fetch[F, I, A]): F[
      DedupedRequest[
        F,
        (
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A]
        )
      ]
    ] =
      fetch
        .batchDedupe(Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7, is._8, is._9, is._10))
        .map { df =>
          df.map { m =>
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
    def fetchTupledLazy[F[_]: Monad, A](implicit fetch: Fetch[F, I, A]): LazyRequest[
      F,
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] =
      fetch
        .batchLazy(Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7, is._8, is._9, is._10))
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
            m.get(is._10)
          )
        }
    def fetchTupledLazy[F[_]: Monad, A](fetch: Fetch[F, I, A]): LazyRequest[
      F,
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] =
      fetch
        .batchLazy(Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7, is._8, is._9, is._10))
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
            m.get(is._10)
          )
        }
  }

  implicit class Tuple11BatchSyntax[I](is: (I, I, I, I, I, I, I, I, I, I, I)) {

    /**
     * Fetches all results in the current tuple, as a map. Will try to batch requests if your Fetch
     * instance supports it.
     */
    def fetchAllMap[F[_], A](implicit fetch: Fetch[F, I, A]): F[Map[I, A]] =
      fetch.batch(
        Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7, is._8, is._9, is._10, is._11)
      )

    def fetchAllDedupeMap[F[_], A](implicit
        fetch: Fetch[F, I, A]
    ): F[DedupedRequest[F, Map[I, A]]] =
      fetch.batchDedupe(
        Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7, is._8, is._9, is._10, is._11)
      )

    def fetchAllLazyMap[F[_]: Applicative, A](implicit
        fetch: Fetch[F, I, A]
    ): LazyRequest[F, Map[I, A]] =
      fetch.batchLazy(
        Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7, is._8, is._9, is._10, is._11)
      )

    /**
     * Fetches all results in the current tuple, retaining the tuple structure. Will try to batch
     * requests if your `Fetch` instance supports it.
     */
    def fetchTupled[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]): F[
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] = fetch
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

    def fetchTupled[F[_]: Functor, A](fetch: Fetch[F, I, A]): F[
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] = fetch
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
    def fetchTupledDedupe[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]): F[
      DedupedRequest[
        F,
        (
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A]
        )
      ]
    ] =
      fetch
        .batchDedupe(
          Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7, is._8, is._9, is._10, is._11)
        )
        .map { df =>
          df.map { m =>
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
    def fetchTupledDedupe[F[_]: Functor, A](fetch: Fetch[F, I, A]): F[
      DedupedRequest[
        F,
        (
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A]
        )
      ]
    ] =
      fetch
        .batchDedupe(
          Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7, is._8, is._9, is._10, is._11)
        )
        .map { df =>
          df.map { m =>
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
    def fetchTupledLazy[F[_]: Monad, A](implicit fetch: Fetch[F, I, A]): LazyRequest[
      F,
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] =
      fetch
        .batchLazy(
          Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7, is._8, is._9, is._10, is._11)
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
            m.get(is._11)
          )
        }
    def fetchTupledLazy[F[_]: Monad, A](fetch: Fetch[F, I, A]): LazyRequest[
      F,
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] =
      fetch
        .batchLazy(
          Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7, is._8, is._9, is._10, is._11)
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
            m.get(is._11)
          )
        }
  }

  implicit class Tuple12BatchSyntax[I](is: (I, I, I, I, I, I, I, I, I, I, I, I)) {

    /**
     * Fetches all results in the current tuple, as a map. Will try to batch requests if your Fetch
     * instance supports it.
     */
    def fetchAllMap[F[_], A](implicit fetch: Fetch[F, I, A]): F[Map[I, A]] =
      fetch.batch(
        Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7, is._8, is._9, is._10, is._11, is._12)
      )

    def fetchAllDedupeMap[F[_], A](implicit
        fetch: Fetch[F, I, A]
    ): F[DedupedRequest[F, Map[I, A]]] =
      fetch.batchDedupe(
        Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7, is._8, is._9, is._10, is._11, is._12)
      )

    def fetchAllLazyMap[F[_]: Applicative, A](implicit
        fetch: Fetch[F, I, A]
    ): LazyRequest[F, Map[I, A]] =
      fetch.batchLazy(
        Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7, is._8, is._9, is._10, is._11, is._12)
      )

    /**
     * Fetches all results in the current tuple, retaining the tuple structure. Will try to batch
     * requests if your `Fetch` instance supports it.
     */
    def fetchTupled[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]): F[
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] = fetch
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

    def fetchTupled[F[_]: Functor, A](fetch: Fetch[F, I, A]): F[
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] = fetch
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
    def fetchTupledDedupe[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]): F[
      DedupedRequest[
        F,
        (
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A]
        )
      ]
    ] =
      fetch
        .batchDedupe(
          Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7, is._8, is._9, is._10, is._11, is._12)
        )
        .map { df =>
          df.map { m =>
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
    def fetchTupledDedupe[F[_]: Functor, A](fetch: Fetch[F, I, A]): F[
      DedupedRequest[
        F,
        (
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A]
        )
      ]
    ] =
      fetch
        .batchDedupe(
          Set(is._1, is._2, is._3, is._4, is._5, is._6, is._7, is._8, is._9, is._10, is._11, is._12)
        )
        .map { df =>
          df.map { m =>
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
    def fetchTupledLazy[F[_]: Monad, A](implicit fetch: Fetch[F, I, A]): LazyRequest[
      F,
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] =
      fetch
        .batchLazy(
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
    def fetchTupledLazy[F[_]: Monad, A](fetch: Fetch[F, I, A]): LazyRequest[
      F,
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] =
      fetch
        .batchLazy(
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

    /**
     * Fetches all results in the current tuple, as a map. Will try to batch requests if your Fetch
     * instance supports it.
     */
    def fetchAllMap[F[_], A](implicit fetch: Fetch[F, I, A]): F[Map[I, A]] =
      fetch.batch(
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

    def fetchAllDedupeMap[F[_], A](implicit
        fetch: Fetch[F, I, A]
    ): F[DedupedRequest[F, Map[I, A]]] =
      fetch.batchDedupe(
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

    def fetchAllLazyMap[F[_]: Applicative, A](implicit
        fetch: Fetch[F, I, A]
    ): LazyRequest[F, Map[I, A]] =
      fetch.batchLazy(
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

    /**
     * Fetches all results in the current tuple, retaining the tuple structure. Will try to batch
     * requests if your `Fetch` instance supports it.
     */
    def fetchTupled[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]): F[
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] = fetch
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

    def fetchTupled[F[_]: Functor, A](fetch: Fetch[F, I, A]): F[
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] = fetch
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
    def fetchTupledDedupe[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]): F[
      DedupedRequest[
        F,
        (
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A]
        )
      ]
    ] =
      fetch
        .batchDedupe(
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
        .map { df =>
          df.map { m =>
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
    def fetchTupledDedupe[F[_]: Functor, A](fetch: Fetch[F, I, A]): F[
      DedupedRequest[
        F,
        (
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A]
        )
      ]
    ] =
      fetch
        .batchDedupe(
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
        .map { df =>
          df.map { m =>
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
    def fetchTupledLazy[F[_]: Monad, A](implicit fetch: Fetch[F, I, A]): LazyRequest[
      F,
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] =
      fetch
        .batchLazy(
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
    def fetchTupledLazy[F[_]: Monad, A](fetch: Fetch[F, I, A]): LazyRequest[
      F,
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] =
      fetch
        .batchLazy(
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

    /**
     * Fetches all results in the current tuple, as a map. Will try to batch requests if your Fetch
     * instance supports it.
     */
    def fetchAllMap[F[_], A](implicit fetch: Fetch[F, I, A]): F[Map[I, A]] =
      fetch.batch(
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

    def fetchAllDedupeMap[F[_], A](implicit
        fetch: Fetch[F, I, A]
    ): F[DedupedRequest[F, Map[I, A]]] =
      fetch.batchDedupe(
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

    def fetchAllLazyMap[F[_]: Applicative, A](implicit
        fetch: Fetch[F, I, A]
    ): LazyRequest[F, Map[I, A]] =
      fetch.batchLazy(
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

    /**
     * Fetches all results in the current tuple, retaining the tuple structure. Will try to batch
     * requests if your `Fetch` instance supports it.
     */
    def fetchTupled[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]): F[
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] = fetch
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

    def fetchTupled[F[_]: Functor, A](fetch: Fetch[F, I, A]): F[
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] = fetch
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
    def fetchTupledDedupe[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]): F[
      DedupedRequest[
        F,
        (
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A]
        )
      ]
    ] =
      fetch
        .batchDedupe(
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
        .map { df =>
          df.map { m =>
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
    def fetchTupledDedupe[F[_]: Functor, A](fetch: Fetch[F, I, A]): F[
      DedupedRequest[
        F,
        (
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A]
        )
      ]
    ] =
      fetch
        .batchDedupe(
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
        .map { df =>
          df.map { m =>
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
    def fetchTupledLazy[F[_]: Monad, A](implicit fetch: Fetch[F, I, A]): LazyRequest[
      F,
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] =
      fetch
        .batchLazy(
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
    def fetchTupledLazy[F[_]: Monad, A](fetch: Fetch[F, I, A]): LazyRequest[
      F,
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] =
      fetch
        .batchLazy(
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

    /**
     * Fetches all results in the current tuple, as a map. Will try to batch requests if your Fetch
     * instance supports it.
     */
    def fetchAllMap[F[_], A](implicit fetch: Fetch[F, I, A]): F[Map[I, A]] =
      fetch.batch(
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

    def fetchAllDedupeMap[F[_], A](implicit
        fetch: Fetch[F, I, A]
    ): F[DedupedRequest[F, Map[I, A]]] =
      fetch.batchDedupe(
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

    def fetchAllLazyMap[F[_]: Applicative, A](implicit
        fetch: Fetch[F, I, A]
    ): LazyRequest[F, Map[I, A]] =
      fetch.batchLazy(
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

    /**
     * Fetches all results in the current tuple, retaining the tuple structure. Will try to batch
     * requests if your `Fetch` instance supports it.
     */
    def fetchTupled[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]): F[
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] = fetch
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

    def fetchTupled[F[_]: Functor, A](fetch: Fetch[F, I, A]): F[
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] = fetch
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
    def fetchTupledDedupe[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]): F[
      DedupedRequest[
        F,
        (
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A]
        )
      ]
    ] =
      fetch
        .batchDedupe(
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
        .map { df =>
          df.map { m =>
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
    def fetchTupledDedupe[F[_]: Functor, A](fetch: Fetch[F, I, A]): F[
      DedupedRequest[
        F,
        (
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A]
        )
      ]
    ] =
      fetch
        .batchDedupe(
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
        .map { df =>
          df.map { m =>
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
    def fetchTupledLazy[F[_]: Monad, A](implicit fetch: Fetch[F, I, A]): LazyRequest[
      F,
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] =
      fetch
        .batchLazy(
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
    def fetchTupledLazy[F[_]: Monad, A](fetch: Fetch[F, I, A]): LazyRequest[
      F,
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] =
      fetch
        .batchLazy(
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

    /**
     * Fetches all results in the current tuple, as a map. Will try to batch requests if your Fetch
     * instance supports it.
     */
    def fetchAllMap[F[_], A](implicit fetch: Fetch[F, I, A]): F[Map[I, A]] =
      fetch.batch(
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

    def fetchAllDedupeMap[F[_], A](implicit
        fetch: Fetch[F, I, A]
    ): F[DedupedRequest[F, Map[I, A]]] =
      fetch.batchDedupe(
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

    def fetchAllLazyMap[F[_]: Applicative, A](implicit
        fetch: Fetch[F, I, A]
    ): LazyRequest[F, Map[I, A]] =
      fetch.batchLazy(
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

    /**
     * Fetches all results in the current tuple, retaining the tuple structure. Will try to batch
     * requests if your `Fetch` instance supports it.
     */
    def fetchTupled[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]): F[
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] = fetch
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

    def fetchTupled[F[_]: Functor, A](fetch: Fetch[F, I, A]): F[
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] = fetch
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
    def fetchTupledDedupe[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]): F[
      DedupedRequest[
        F,
        (
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A]
        )
      ]
    ] =
      fetch
        .batchDedupe(
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
        .map { df =>
          df.map { m =>
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
    def fetchTupledDedupe[F[_]: Functor, A](fetch: Fetch[F, I, A]): F[
      DedupedRequest[
        F,
        (
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A]
        )
      ]
    ] =
      fetch
        .batchDedupe(
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
        .map { df =>
          df.map { m =>
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
    def fetchTupledLazy[F[_]: Monad, A](implicit fetch: Fetch[F, I, A]): LazyRequest[
      F,
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] =
      fetch
        .batchLazy(
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
    def fetchTupledLazy[F[_]: Monad, A](fetch: Fetch[F, I, A]): LazyRequest[
      F,
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] =
      fetch
        .batchLazy(
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

    /**
     * Fetches all results in the current tuple, as a map. Will try to batch requests if your Fetch
     * instance supports it.
     */
    def fetchAllMap[F[_], A](implicit fetch: Fetch[F, I, A]): F[Map[I, A]] =
      fetch.batch(
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

    def fetchAllDedupeMap[F[_], A](implicit
        fetch: Fetch[F, I, A]
    ): F[DedupedRequest[F, Map[I, A]]] =
      fetch.batchDedupe(
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

    def fetchAllLazyMap[F[_]: Applicative, A](implicit
        fetch: Fetch[F, I, A]
    ): LazyRequest[F, Map[I, A]] =
      fetch.batchLazy(
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

    /**
     * Fetches all results in the current tuple, retaining the tuple structure. Will try to batch
     * requests if your `Fetch` instance supports it.
     */
    def fetchTupled[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]): F[
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] = fetch
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

    def fetchTupled[F[_]: Functor, A](fetch: Fetch[F, I, A]): F[
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] = fetch
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
    def fetchTupledDedupe[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]): F[
      DedupedRequest[
        F,
        (
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A]
        )
      ]
    ] =
      fetch
        .batchDedupe(
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
        .map { df =>
          df.map { m =>
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
    def fetchTupledDedupe[F[_]: Functor, A](fetch: Fetch[F, I, A]): F[
      DedupedRequest[
        F,
        (
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A]
        )
      ]
    ] =
      fetch
        .batchDedupe(
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
        .map { df =>
          df.map { m =>
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
    def fetchTupledLazy[F[_]: Monad, A](implicit fetch: Fetch[F, I, A]): LazyRequest[
      F,
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] =
      fetch
        .batchLazy(
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
    def fetchTupledLazy[F[_]: Monad, A](fetch: Fetch[F, I, A]): LazyRequest[
      F,
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] =
      fetch
        .batchLazy(
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

    /**
     * Fetches all results in the current tuple, as a map. Will try to batch requests if your Fetch
     * instance supports it.
     */
    def fetchAllMap[F[_], A](implicit fetch: Fetch[F, I, A]): F[Map[I, A]] =
      fetch.batch(
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

    def fetchAllDedupeMap[F[_], A](implicit
        fetch: Fetch[F, I, A]
    ): F[DedupedRequest[F, Map[I, A]]] =
      fetch.batchDedupe(
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

    def fetchAllLazyMap[F[_]: Applicative, A](implicit
        fetch: Fetch[F, I, A]
    ): LazyRequest[F, Map[I, A]] =
      fetch.batchLazy(
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

    /**
     * Fetches all results in the current tuple, retaining the tuple structure. Will try to batch
     * requests if your `Fetch` instance supports it.
     */
    def fetchTupled[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]): F[
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] = fetch
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

    def fetchTupled[F[_]: Functor, A](fetch: Fetch[F, I, A]): F[
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] = fetch
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
    def fetchTupledDedupe[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]): F[
      DedupedRequest[
        F,
        (
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A]
        )
      ]
    ] =
      fetch
        .batchDedupe(
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
        .map { df =>
          df.map { m =>
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
    def fetchTupledDedupe[F[_]: Functor, A](fetch: Fetch[F, I, A]): F[
      DedupedRequest[
        F,
        (
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A]
        )
      ]
    ] =
      fetch
        .batchDedupe(
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
        .map { df =>
          df.map { m =>
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
    def fetchTupledLazy[F[_]: Monad, A](implicit fetch: Fetch[F, I, A]): LazyRequest[
      F,
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] =
      fetch
        .batchLazy(
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
    def fetchTupledLazy[F[_]: Monad, A](fetch: Fetch[F, I, A]): LazyRequest[
      F,
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] =
      fetch
        .batchLazy(
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

    /**
     * Fetches all results in the current tuple, as a map. Will try to batch requests if your Fetch
     * instance supports it.
     */
    def fetchAllMap[F[_], A](implicit fetch: Fetch[F, I, A]): F[Map[I, A]] =
      fetch.batch(
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

    def fetchAllDedupeMap[F[_], A](implicit
        fetch: Fetch[F, I, A]
    ): F[DedupedRequest[F, Map[I, A]]] =
      fetch.batchDedupe(
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

    def fetchAllLazyMap[F[_]: Applicative, A](implicit
        fetch: Fetch[F, I, A]
    ): LazyRequest[F, Map[I, A]] =
      fetch.batchLazy(
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

    /**
     * Fetches all results in the current tuple, retaining the tuple structure. Will try to batch
     * requests if your `Fetch` instance supports it.
     */
    def fetchTupled[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]): F[
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] = fetch
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

    def fetchTupled[F[_]: Functor, A](fetch: Fetch[F, I, A]): F[
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] = fetch
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
    def fetchTupledDedupe[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]): F[
      DedupedRequest[
        F,
        (
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A]
        )
      ]
    ] =
      fetch
        .batchDedupe(
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
        .map { df =>
          df.map { m =>
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
    def fetchTupledDedupe[F[_]: Functor, A](fetch: Fetch[F, I, A]): F[
      DedupedRequest[
        F,
        (
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A]
        )
      ]
    ] =
      fetch
        .batchDedupe(
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
        .map { df =>
          df.map { m =>
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
    def fetchTupledLazy[F[_]: Monad, A](implicit fetch: Fetch[F, I, A]): LazyRequest[
      F,
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] =
      fetch
        .batchLazy(
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
    def fetchTupledLazy[F[_]: Monad, A](fetch: Fetch[F, I, A]): LazyRequest[
      F,
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] =
      fetch
        .batchLazy(
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

    /**
     * Fetches all results in the current tuple, as a map. Will try to batch requests if your Fetch
     * instance supports it.
     */
    def fetchAllMap[F[_], A](implicit fetch: Fetch[F, I, A]): F[Map[I, A]] =
      fetch.batch(
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

    def fetchAllDedupeMap[F[_], A](implicit
        fetch: Fetch[F, I, A]
    ): F[DedupedRequest[F, Map[I, A]]] =
      fetch.batchDedupe(
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

    def fetchAllLazyMap[F[_]: Applicative, A](implicit
        fetch: Fetch[F, I, A]
    ): LazyRequest[F, Map[I, A]] =
      fetch.batchLazy(
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

    /**
     * Fetches all results in the current tuple, retaining the tuple structure. Will try to batch
     * requests if your `Fetch` instance supports it.
     */
    def fetchTupled[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]): F[
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] = fetch
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

    def fetchTupled[F[_]: Functor, A](fetch: Fetch[F, I, A]): F[
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] = fetch
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
    def fetchTupledDedupe[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]): F[
      DedupedRequest[
        F,
        (
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A]
        )
      ]
    ] =
      fetch
        .batchDedupe(
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
        .map { df =>
          df.map { m =>
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
    def fetchTupledDedupe[F[_]: Functor, A](fetch: Fetch[F, I, A]): F[
      DedupedRequest[
        F,
        (
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A]
        )
      ]
    ] =
      fetch
        .batchDedupe(
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
        .map { df =>
          df.map { m =>
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
    def fetchTupledLazy[F[_]: Monad, A](implicit fetch: Fetch[F, I, A]): LazyRequest[
      F,
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] =
      fetch
        .batchLazy(
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
    def fetchTupledLazy[F[_]: Monad, A](fetch: Fetch[F, I, A]): LazyRequest[
      F,
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] =
      fetch
        .batchLazy(
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

    /**
     * Fetches all results in the current tuple, as a map. Will try to batch requests if your Fetch
     * instance supports it.
     */
    def fetchAllMap[F[_], A](implicit fetch: Fetch[F, I, A]): F[Map[I, A]] =
      fetch.batch(
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

    def fetchAllDedupeMap[F[_], A](implicit
        fetch: Fetch[F, I, A]
    ): F[DedupedRequest[F, Map[I, A]]] =
      fetch.batchDedupe(
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

    def fetchAllLazyMap[F[_]: Applicative, A](implicit
        fetch: Fetch[F, I, A]
    ): LazyRequest[F, Map[I, A]] =
      fetch.batchLazy(
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

    /**
     * Fetches all results in the current tuple, retaining the tuple structure. Will try to batch
     * requests if your `Fetch` instance supports it.
     */
    def fetchTupled[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]): F[
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] = fetch
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

    def fetchTupled[F[_]: Functor, A](fetch: Fetch[F, I, A]): F[
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] = fetch
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
    def fetchTupledDedupe[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]): F[
      DedupedRequest[
        F,
        (
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A]
        )
      ]
    ] =
      fetch
        .batchDedupe(
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
        .map { df =>
          df.map { m =>
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
    def fetchTupledDedupe[F[_]: Functor, A](fetch: Fetch[F, I, A]): F[
      DedupedRequest[
        F,
        (
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A]
        )
      ]
    ] =
      fetch
        .batchDedupe(
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
        .map { df =>
          df.map { m =>
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
    def fetchTupledLazy[F[_]: Monad, A](implicit fetch: Fetch[F, I, A]): LazyRequest[
      F,
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] =
      fetch
        .batchLazy(
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
    def fetchTupledLazy[F[_]: Monad, A](fetch: Fetch[F, I, A]): LazyRequest[
      F,
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] =
      fetch
        .batchLazy(
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

    /**
     * Fetches all results in the current tuple, as a map. Will try to batch requests if your Fetch
     * instance supports it.
     */
    def fetchAllMap[F[_], A](implicit fetch: Fetch[F, I, A]): F[Map[I, A]] =
      fetch.batch(
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

    def fetchAllDedupeMap[F[_], A](implicit
        fetch: Fetch[F, I, A]
    ): F[DedupedRequest[F, Map[I, A]]] =
      fetch.batchDedupe(
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

    def fetchAllLazyMap[F[_]: Applicative, A](implicit
        fetch: Fetch[F, I, A]
    ): LazyRequest[F, Map[I, A]] =
      fetch.batchLazy(
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

    /**
     * Fetches all results in the current tuple, retaining the tuple structure. Will try to batch
     * requests if your `Fetch` instance supports it.
     */
    def fetchTupled[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]): F[
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] = fetch
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

    def fetchTupled[F[_]: Functor, A](fetch: Fetch[F, I, A]): F[
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] = fetch
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
    def fetchTupledDedupe[F[_]: Functor, A](implicit fetch: Fetch[F, I, A]): F[
      DedupedRequest[
        F,
        (
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A]
        )
      ]
    ] =
      fetch
        .batchDedupe(
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
        .map { df =>
          df.map { m =>
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
    def fetchTupledDedupe[F[_]: Functor, A](fetch: Fetch[F, I, A]): F[
      DedupedRequest[
        F,
        (
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A],
            Option[A]
        )
      ]
    ] =
      fetch
        .batchDedupe(
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
        .map { df =>
          df.map { m =>
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
    def fetchTupledLazy[F[_]: Monad, A](implicit fetch: Fetch[F, I, A]): LazyRequest[
      F,
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] =
      fetch
        .batchLazy(
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
    def fetchTupledLazy[F[_]: Monad, A](fetch: Fetch[F, I, A]): LazyRequest[
      F,
      (
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A],
          Option[A]
      )
    ] =
      fetch
        .batchLazy(
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
