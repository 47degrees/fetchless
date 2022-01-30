# fetchless (proof of concept)
A port of the Fetch library to Tagless Final style

## Key Differences
* Encoded in tagless final style, instead of as a data structure
* No need for explicit support for logging, timing, rate-limiting, timeouts, etc. as those can use your base effect type.
* Less runtime overhead due to being implemented in terms of existing effects, rather than as its own effect.
* Orders of magnitude faster than original Fetch (identical DataSource/Fetch instances, benchmark not available yet)
* Core code extremely small (~50loc), most code is actually just implicit syntax wrappers for collections & tuples.
* No required type class dependencies to call fetching methods (after creating a `Fetch` instance)

## Motivation
In Fetch 3.0.0, I made a decision after consulting with a couple other 47ers to remove the guarantee for implicit batching on `sequence`/`traverse` calls due to the behavior breaking from a Cats version upgrade.
It's entirely possible to fix this (and I've already submitted a PR) without breaking anything, but it seemed best at the time with what limited perspective I had that maybe we should instead introduce explicit batching syntax.
In hindsight, I think this makes Fetch somewhat less useful and have considered the alternatives.

The entire point of Fetch, in a sense, is to auto-batch requests wherever possible.
There are some pros and cons with this approach:

Pros:
* You don't have to explicitly call `Parallel` methods such as `parTupled`/`parTraverse`/`parSequence` in order to invoke concurrency and batching, and can just use `.tupled`/`.traverse`/`.sequence` as normal.
* You can treat it as an effect type on its own, and use it in any code that depends on the `Monad` type class.

Cons:
* The abstraction breaks down slightly in assuming that users will know and understand that `flatMap` is strictly sequential, whereas `tupled`/`traverse` might not be. So to optimize fetches, the user needs to be aware of whether or not these `Applicative` methods can be used instead of monadic ones, and in that case it could be argued that the user might as well just explicitly ask for batching in those cases (since those functions require an applicative-friendly signature)
* Requires custom code for features such as logging, timing, timeouts, etc. for any intermediate fetch.
* Must be interpreted at runtime from its reified form, which also implies leaking the `Concurrent` implementation detail for when you plug in an effect type.
* Can break at a moment's notice if any implementation details in Cats that this depends on for auto-batching support lawfully change (as did happen before 3.0.0)

As listed above in the "key differences" section, a tagless approach has numerous advantages.
The two biggest advantages are speed and API flexibility.

### Speed
While not properly benchmarked extensively yet (I wrote this up very quickly and will expand on this later if necessary), I've ran a small local test that requests 50,000 arbitrary integers from a Map.
In this test, Fetch retrieves from a DataSource and fetchless retrieves from a `Fetch` instance that is similarly configured (and probably less efficiently, too, since it needs to convert collections).
The test was performed with Fetch v2.1.1 (v3.0.0 is a bit slower, and needs optimization) using `sequence` as the batching method for Fetch and `fetchAll`, a similar syntax method, in fetchless.
In Fetch, this took about 7445548000ns, while in Fetchless this took 17683800ns (about 421x as fast).
In the real world, the difference is likely not nearly as profound and when testing with smaller numbers of elements the difference is not nearly as large, but it should tell you that a tagless final approach is fundamentally more efficient than encoding your fetch requests to a data structure before running them.

### Flexibility
There are a couple details in Fetch that make it not very flexible in the API department:

* `DataSource` instances must contain a `Concurrent` instance for the effect type, even if it is not used in an implementation, simply because it is possible that it could be.

* `Fetch.run` and similar require the `Concurrent` type class, which means that if you are several layers deep into your program you have to depend on this rather heavy type class for your effect type rather than something more simple.

* Because a `Fetch` is its own effect, implementing features that happen between sequenced fetches requires basically duplicating the API of Cats Effect, in Fetch, rather than relying on existing functionality that is less likely to need maintenance.

Fetchless solves this by making the following decisions:

* A `Fetch` instance (similar to `DataSource`) has only two main methods: `single` and `batch`, and no dependency on any specific type class.
* The `Fetch` instance itself should decide if parallelism should be used in cases of fetching multiple IDs in a batch.
* The paradigm becomes less about "invisibly batching fetches", and more about describing your data models as "fetchable" by providing a `Fetch` instance for them. Given a `Fetch` instance exists for some type in the current scope, you can use `.fetch`/`.fetchAll`/`.fetchTupled` syntax on any ID value or collection.
* For all additional functionality, we can use the base effect system, reducing maintenance burden as well as increasing efficiency.

## Examples

To demonstrate the way this works, consider the following example code:

```scala
import cats.effect.IO
import cats.syntax.all._
import fetch.DataSource
import fetch.{Fetch => OGFetch}

//Given an Int, returns it back
val testDataSource = new DataSource[IO, Int, Int] {
  def data: Data[Int, Int] = new Data[Int, Int] {
    def name: String = "TestDataSource"
  }

  implicit def CF: Concurrent[IO] = Concurrent[IO]

  def fetch(id: Int): IO[Option[Int]] = IO.pure(Some(id))

  override def batch(ids: NonEmptyList[Int]): IO[Map[Int, Int]] =
    IO.pure(ids.toList.map(i => i -> i).toMap)

}

val testProgram: IO[List[Int]] = OGFetch.run(List(1, 2, 3).map(i => Fetch(i, testDataSource)).sequence)
```

For Fetchless, the equivalent code would look like this:

```scala
import cats.effect.IO
import cats.syntax.all._
import fetchless.Fetch
import fetchless.syntax._

//Constructor for Fetch instances that will run all batches as single fetches in a strict linear sequence.
implicit val testFetch: Fetch[IO, Int, Int] = Fetch.singleSequenced(i => IO.pure(i))

val testProgram: IO[Map[Int, Int]] = List(1, 2, 3).fetchAll
```

There are a couple minor differences in the API type signatures, but the general idea is the same.
You can fetch single items with one ID, and you can request multiple IDs at once in a batch.
The actual implementation of batches is entirely contained in the `Fetch` instance you create, so you no longer have to
specify if you want to run fetches in parallel or in a linear sequence.
As long as you are fetching more than one ID at a time, it is guaranteed to use whatever behavior the batch implementation of your `Fetch` instance is designed to do.

Here are the current constructors for `Fetch` instances:

* `singleSequenced` - Only runs single fetches, and batches are ran as sequential. Requires `Applicative`.
* `singleParallel` - Runs batches in parallel. Requires `Applicative` and `Parallel`.
* `batchable` - Allows you to specify your own functions for `single` and `batch` fetches. No type class constraints (assumed the user will have their own).
* `batchOnly` - Runs batches only, and encodes single fetches as a one-element batch. Requires `Applicative`. Whether or not batches are in parallel depends on the user's supplied function for the batch.
* `const` - A constant `Fetch` that retrieves from an in-memory map.

And of course you can always create your own instance, though `batchable` winds up being the same thing with less boilerplate.

To get around the problem of how to do auto-batching, the solution is to simply defer to the current `Fetch` implementation that the user wires in.
The important details should be just that the user is requesting data, either one-at-a-time or in a batch.
Exactly how that batch is acquired, just like the original Fetch library, is unimportant at the point it is being used.
The user does not select `traverse` vs `parTraverse`, and so on, but instead they can use the following syntax:

```scala
val exampleId = 5

val singleFetch: IO[Option[Int]] = 5.fetch[Id, Int, Int] //Can also manually supply the Fetch instance

val effectfulFetch: IO[Option[Int]] = IO(5).fetch[Int]

val traverseFetch: IO[Map[Int, Int]] = List(5).fetchAll //Also configured to work on tuples

val tupledFetch: IO[(Option[Int], Option[Int], Option[Int])] = (5, 5, 5).fetchTupled
```

This also side-steps the problem of needing to provide explicit support for popular user-requested features, since all fetches are always directly implemented in terms of `F[_]` and can be manipulated with whatever libraries and combinators users already use (including whatever is in the CE3 standard library)
