package fetchless.doobie

import doobie.Query
import doobie.Query0
import doobie.ConnectionIO
import fetchless.Fetch
import doobie.syntax._
import doobie.util.query

object DoobieFetch {

  /**
   * Creates a `Fetch` instance that makes one query at a time. For parallelism, you will need to
   * handle that at the transactor level by running each `ConnectionIO`.
   */
  def forQuery[I, A](fetchId: String)(q: Query[I, A]): Fetch[ConnectionIO, I, A] =
    Fetch.singleSequenced[ConnectionIO, I, A](fetchId) { i =>
      q.option(i)
    }

  /**
   * Creates a `Fetch` instance that makes sequential queries but uses a special optimized batch
   * query for batches. For parallelism, you will need to handle that at the transactor level by
   * running each `ConnectionIO`.
   */
  def forBatchableQuery[I, A](
      fetchId: String
  )(single: Query[I, A])(batch: Set[I] => Query0[(I, A)]): Fetch[ConnectionIO, I, A] =
    Fetch.batchable[ConnectionIO, I, A](fetchId)(i => single.option(i))(s => batch(s).toMap)
}
