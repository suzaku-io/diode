# Async Virtual Collections

> Note this is a *Work In Progress* feature

In most applications the UI shows only a small fraction of the data residing on the server. For example in GitHub you have organizations that have repositories
that have issues that consist of events. The data can be viewed as a large, _directional graph_ or a hierarchy. At any point of time, the UI requires just a
small subset of this graph to be able to display the data correctly.

Making explicit calls for data is both tedious and bad for performance, because the client is doing no caching whatsoever. Adding caching makes requests even
more tedious and also error prone. Systems like [GraphQL](https://facebook.github.io/react/blog/2015/05/01/graphql-introduction.html) and 
[Falcor](https://netflix.github.io/falcor/starter/what-is-falcor.html) work around these problems by presenting the data as _virtual collections_.
 
As we already have the application model represented as an immutable hierarchy, wouldn't it be nice to automatically represent data on the server in the model
itself?

## Pot Collections

Diode defines a `PotCollection[K, V]` trait which is implemented in `PotMap` and `PotVector`. These represent virtual collections where the data is fetched
asynchronously when needed. Collections do not handle fetching themselves, but require an implementation of the `Fetch[K]` trait to be passed in the
constructor.

`PotCollection` works much like a normal immutable Scala collection as you can `get` values, `remove` them and get an `updated` collection with new values. In
addition to these basic features there are also methods for `refresh`ing the content of selected values. Note that `PotCollection` does not implement any of
the Scala collection traits, so they are not compatible with native collections as such. For example `get` returns a `Pot[V]` instead of an `Option[Pot[V]]` as
a normal collection would.

The values in `PotCollection` are always of type `Pot[V]`, giving you the multi-state functionality you are already familiar with. If your application is
already using `Pot` values, you can easily plug in `PotCollection`s without much effort.

### Usage

```scala
def renderUser(userId: String, users: PotMap[String, User]) = {
  users(userId).map { user =>
    div(span(cls := "name", user.name), img(cls := "profile", src := user.picUrl))
  } getOrElse div()
}
```

In this example function the `users` collection is a `PotMap` using `String` as a key. We simply get the value out of the collection by using `apply(key)` and
use `map` to transform it into a Scalatags element. In case there is no value (at the moment), an empty `div` is returned.

Assuming the `userId` does not exist in the `PotMap`, the call to `apply` (or `get`) will result in a call to fetch its content. As this is an asynchronous
call (typically dispatching an appropriate action), we will not get the value right away but will return a `Pending` instead. When the fetch is completed, the
`PotMap` is updated with the new value, which is returned in the next call to `apply`. As with `Pot`s in general, a model update does not automatically trigger
a view rendering, so you need to use a listener to track model changes.

### Fetch Actions

As with a regular `Pot` it makes sense to use `AsyncAction` to handle the details of fetching data for a `PotCollection`. `AsyncAction` provides handlers for
both `PotMap` and `PotVector`, to update values given a set of keys.

```scala
def mapHandler[K, V, A <: Traversable[(K, Pot[V])], M, P <: AsyncAction[A, P]](keys: Set[K])
  (implicit ec: ExecutionContext) = {
  require(keys.nonEmpty)
  (action: AsyncAction[A, P], handler: ActionHandler[M, PotMap[K, V]], updateEffect: Effect) => {
    import PotState._
    import handler._
    // updates/adds only those values whose key is in the `keys` set
    def updateInCollection(f: Pot[V] => Pot[V], default: Pot[V]): PotMap[K, V] = {
      // update existing values
      value.map { (k, v) =>
        if (keys.contains(k))
          f(v)
        else
          v
      } ++ (keys -- value.keySet).map(k => k -> default) // add new ones
    }
    action.state match {
      case PotEmpty =>
        updated(updateInCollection(_.pending(), Pending()), updateEffect)
      case PotPending =>
        noChange
      case PotUnavailable =>
        noChange
      case PotReady =>
        updated(value.updated(action.result.get))
      case PotFailed =>
        val ex = action.result.failed.get
        updated(updateInCollection(_.fail(ex), Failed(ex)))
    }
  }
}
```

It works quite similarly to the regular `AsyncAction.handler` but instead of updating the whole collection, only a subset of values in the collection are
updated, based on the set of given `keys`. The `updateEffect` must update values for all the `keys`, otherwise some of them will be left in `Pending` state.
It's ok to have multiple simultaneous updates running for the same `PotCollection` but you should make sure they do not use overlapping key sets.

An example fetch implementation could be like following:

```scala
case class User(id:String, name: String)

// define a AsyncAction for updating users
case class UpdateUsers(
  keys: Set[String],
  state: PotState = PotState.PotEmpty,
  result: Try[Map[String, Pot[User]]] = Failure(new AsyncAction.PendingException)
) extends AsyncAction[Map[String, Pot[User]], UpdateUsers] {
  def next(newState: PotState, newValue: Try[Map[String, Pot[User]]]) =
    UpdateUsers(keys, newState, newValue)
}

// an implementation of Fetch for users
class UserFetch(dispatch: Dispatcher) extends Fetch[String] {
  override def fetch(key: String): Unit =
    dispatch(UpdateUsers(keys = Set(key)))
  override def fetch(keys: Traversable[String]): Unit =
    dispatch(UpdateUsers(keys = Set() ++ keys))
}

// function to load a set of users based on keys
def loadUsers(keys: Set[String]): Future[Map[String, Pot[User]]]

// handle the action
override def handle = {
  case action: UpdateUsers =>
    val updateEffect = action.effect(loadUsers(action.keys))(identity)
    action.handleWith(this, updateEffect)(AsyncAction.mapHandler(action.keys))
}
```

## Pot Streams

Similar to `PotCollection` but with a slightly different API, Diode provides a `PotStream` for handling streaming data. Its use cases include things like chat
room messages, monitoring events or infinite scrollers. Unlike `PotMap` or `PotVector` that allow more or less random access to the remote data, a `PotStream`
always consists of consecutive entries with unique identifiers. The values stored in a `PotStream` are not automatically `Pot[V]`s but direct values because
`PotStream` does not allow access to entries that are not currently present. You may of course choose to store `Pot[V]` if you wish to do so.

Each value in `PotStream` is wrapped in a `StreamValue` case class that provides indices to previous and next entries in the stream.

```scala
case class StreamValue[K, V](key: K, value: Pot[V], stream: PotStream[K, V], prevKey: Option[K], nextKey: Option[K])
```

You can call `get(key)` to get a reference to a `StreamValue` that you can use to navigate up and down the stream with `next` and `prev` methods. If you need
to iterate over all present entries in the stream, use `iterator` to get an instance of `Iterator[(K, V)]`
 
To update data in a `PotStream` use `update` (only for existing entries), `append` or `prepend`. In a typical scenario the client is not directly adding
new data to the stream but is requesting updates from the server, or updates are automatically delivered over WebSocket or so. The client can, however, initiate
updates by calling `refresh`, `refreshNext` or `refreshPrev` methods.