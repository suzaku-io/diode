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

As with a regular `Pot` it makes sense to use `PotAction` to handle the details of fetching data for a `PotCollection`. `PotAction` provides handlers for
both `PotMap` and `PotVector`, to update values given a set of keys.

```scala
def mapHandler[K, V, A <: Traversable[(K, Pot[V])], M, P <: PotAction[A, P]]
  (keys: Set[K], retryPolicy: RetryPolicy = Retry.None)
  (implicit ec: ExecutionContext) = {
  require(keys.nonEmpty)
  (action: PotAction[A, P], handler: ActionHandler[M, PotMap[K, V]], updateEffect: Effect) => {
    import PotState._
    import handler._
    // updates only those values whose key is in the `keys` list
    def updateInCollection(f: Pot[V] => Pot[V]): PotMap[K, V] = {
      value.map { (k, v) =>
        if (keys.contains(k))
          f(v)
        else
          v
      }
    }
    action.state match {
      case PotEmpty =>
        updated(updateInCollection(_.pending(retryPolicy)), updateEffect)
      case PotPending =>
        noChange
      case PotUnavailable =>
        noChange
      case PotReady =>
        updated(value.updated(action.value.get))
      case PotFailed =>
        // get one retry policy, they're all the same
        val rp = value.get(keys.head).retryPolicy
        rp.retry(action.value, updateEffect) match {
          case Right((nextPolicy, retryEffect)) =>
            updated(updateInCollection(_.pending(nextPolicy)), retryEffect)
          case Left(ex) =>
            updated(updateInCollection(_.fail(ex)))
        }
    }
  }
}
```

It works quite similarly to the regular `PotAction.handler` but instead of updating the whole collection, only a subset of values in the collection are updated,
based on the set of given `keys`. The `updateEffect` must update values for all the `keys`, otherwise some of them will be left in `Pending` state. It's ok to
have multiple simultaneous updates running for the same `PotCollection` but you should make sure they do not use overlapping key sets.

An example fetch implementation could be like following:

```scala
case class User(id:String, name: String)

// define a PotAction for updating users
case class UpdateUsers(value: Pot[Map[String, User]] = Empty, keys: Set[String]) 
  extends PotAction[Map[String, User], UpdateUsers] {
  def next(newValue: Pot[Map[String, User]]) = UpdateUsers(newValue, keys)
}

// an implementation of Fetch for users
class UserFetch(dispatch: Dispatcher) extends Fetch[String] {
  override def fetch(key: String): Unit = 
    dispatch(UpdateUsers(keys = Set(key)))
  override def fetch(start: String, end: String): Unit = 
    ??? // range is not supported
  override def fetch(keys: Traversable[String]): Unit = 
    dispatch(UpdateUsers(keys = Set() ++ keys))
}

// function to load a set of users based on keys
def loadUsers(keys: Set[String]): Future[Map[String, Option[User]]]

// handle the action
override def handle = {
  case action: UpdateUsers =>
    val updateEffect = action.effect(loadUsers(action.keys))(users => users)
    action.handleWith(this, updateEffect)(PotAction.mapHandler(action.keys, Retry(3)))
}
```
