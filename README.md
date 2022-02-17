# fetchless (proof of concept)
A port of the Fetch library to Tagless Final style

## Key Differences
* Encoded in tagless final style first, instead of as a data structure
* No need for explicit support for logging, timing, rate-limiting, timeouts, etc. as those can use your base effect type.
* Less runtime overhead due to being implemented in terms of existing effects, rather than as its own effect.
* Potentially orders of magnitude faster than original Fetch
* Core code is very small, most code is actually just implicit syntax wrappers for collections & tuples.
* No required type class dependencies to call fetching methods (after creating a `Fetch` instance)

## Motivation
In Fetch 3.0.0, I made a decision to remove the guarantee for implicit batching on `sequence`/`traverse` calls due to the behavior breaking from a Cats version upgrade.
This is reverted as of 3.1.0, but it seemed best at the time with what limited perspective I had that maybe we should instead introduce explicit batching syntax.
In hindsight, I think this makes Fetch somewhat less useful and have considered the alternatives.

The entire point of Fetch, in a sense, is to auto-batch and parallelize requests wherever possible.
It does this by encoding a DSL for fetches as an explicit effect type that is interpreted later, using a custom `Monad` instance with overrides on certain applicative methods.
There are some pros and cons with this approach:

Pros:
* You don't have to explicitly call `Parallel` methods such as `parTupled`/`parTraverse`/`parSequence` in order to invoke concurrency and batching, and can just use `.tupled`/`.traverse`/`.sequence` as normal.
* You can treat it as an effect type on its own, and use it in any code that depends on the `Monad` type class.

Cons:
* The abstraction breaks down slightly in assuming that users will know and understand that `flatMap` is strictly sequential, whereas `tupled`/`traverse` might not be. So to optimize fetches, the user needs to be aware of whether or not these `Applicative` methods can be used instead of monadic ones, and in that case it could be argued that the user might as well just explicitly ask for batching in those cases (since those functions require an applicative-friendly signature anyhow)
* Requires custom code for features such as logging, timing, timeouts, etc. for any intermediate fetch.
* Must be interpreted at runtime from its reified form, which also implies leaking the `Concurrent` implementation detail for when you plug in an effect type.
* Can break at a moment's notice if any implementation details in Cats that this depends on for auto-batching support lawfully change (as did happen before 3.0.0)

As listed above in the "key differences" section, a tagless approach has numerous advantages.
The two biggest advantages are speed and API flexibility.

### Speed
While not properly benchmarked extensively yet (I wrote this up very quickly and will expand on this later), I've ran some local benchmarks that confirm my initial suspicions about the speed of Fetch vs Fetchless.
On a fundamental level, it should make sense that a "Free"-style DSL is slower to at least some degree than a "tagless final"-style one.
This is because of how they are encoded, since the tagless version is much more direct and has less overhead by definition.

Here is some code from a (currently local, unpublished) benchmark that should get the point across (time in nanoseconds)

(average, in nanoseconds, over 40 runs)

```
Immediate fetch traverse result
1.12204425E7
Immediate deduped fetch traverse result
1.58746375E7
LazyFetch traverse result
5.33710375E7
LazyBatch set result
6.621083E7
LazyBatch traverse result
1.01090145E8
LazyFetch parTraverse result
1.54424665E8
```

Doing an identical benchmark on Fetch, looks like the following:

(average, in nanoseconds, over 40 runs)
```
Fetch traverse result
7.6349248325E9
```

In each case, the benchmark involves traversing over a list of 50,000 integers and performing a fetch, except for `LazyBatch set` which is an explicit batch and not a traversal.
For the `immediate` case up top, that is for a direct fetch with no deduping or auto-batching support.
`LazyFetch` supports deduping and sequencing, but not auto-batching, and `LazyBatch` is an applicative type that supports batches only.
So in the absolute worst case for Fetchless, it appears that starting with `LazyFetch` and using `parTraverse` to re-encode as a single `LazyBatch` takes well over an order of magnitude less time to perform.
It's still possible that future changes will bridge this gap slowly, as more features and functionality are added or possible bugs are fixed, but this is a very promising start and shows that even if you choose the slowest possible fetch option in Fetchless, it is still faster than Fetch.

### Flexibility
There are a couple details in Fetch that make it not very flexible in the API department:

* `DataSource` instances must contain a `Concurrent` instance for the effect type, even if it is not used in an implementation, simply because it is possible that it could be.
* It must also specify an execution style and a maximum count for batches, if any. These feel like leaking implementation details that could just be closed over in the implementation.
* `Fetch.run` and similar require the `Concurrent` type class among others, which means that if you are several layers deep into your program you have to depend on this rather heavy type class for your effect type rather than something more simple like `Monad`.
* Because a `Fetch` is its own effect, implementing features that happen between sequenced fetches requires implementing the functionality inside the library, rather than providing some kind of way for users to hook in and interleve effects.

Fetchless solves this by making the following decisions:

* A `Fetch` instance (similar to `DataSource`) has only two main methods: `single` and `batch`, and no dependency on any specific type class.
* The `Fetch` instance itself should decide if parallelism or maximum-batch-sizes should be considered in cases of fetching multiple IDs in a batch.
* The paradigm becomes less about "invisibly batching fetches", and more about describing your data models as "fetchable" by providing a `Fetch` instance for them. Given a `Fetch` instance exists for some type in the current scope, you can use `.fetch`/`.fetchAll`/`.fetchTupled`/`.fetchLazy` syntax on any ID value or collection.
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
* `echo` - A `Fetch` that returns the input the user provides, great for tests.

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

## DedupedFetch

One feature not supported in a standard fetch is deduping, since that requires some kind of context to pass around.
I've added a type `DedupedFetch` and operators to fetch using this provided context, so that in any context you can make very efficient fetches that depend on previous fetches for caching and deduplication.

## LazyFetch and LazyBatch

The `Fetch` instance can not only deduplicate fetches with `DedupedFetch` but it can also create lazy fetches that approximate the behaavior found in the original `Fetch` library with regards to not only deduplication, but also automatic batching.

A `LazyFetch` is created when you call `.fetchLazy` on an ID, or call `.singleLazy`/`.batchLazy` on a `Fetch` instance.
It is essentially just a wrapper type that encodes the intention to chain fetch calls together, so you can `flatMap` multiples of them in sequence and raise effects into its context with `LazyFetch.liftF`/`LazyBatch.liftF`.

`LazyBatch` is the parallel of `LazyFetch` which is not monadic, but only `Applicative` and represents the intention to group multiple fetches into a batch.
You can combine them together with `.tupled` and `.traverse`-style methods that work for any `Applicative` and it will guarantee to put all of your requests into a single batch per-fetch-instance.
However, you cannot chain them together, so you need to convert back and forth between `LazyFetch` and `LazyBatch` to keep dependent sequencing along with auto-batching.
This is done with the `Parallel` instance for `LazyFetch` so you can call `.parTupled`/`.parTraverse` and so on on your independent fetches, and get automatic deduplication and batching just like you did in the original Fetch library, only now you must explicitly opt-in to auto-batching.

For more details, please see the included tests.
