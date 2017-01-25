# Changes

## 1.1.1

- New `zoomTo` macro to simplify common `zoomRW` use-cases. For example what used to be
```scala
circuit.zoomRW(_.a)((m, v) ⇒ m.copy(a = v)).zoomRW(_.i)((m, v) ⇒ m.copy(i = v))
```
can now be expressed with
```scala
circuit.zoomTo(_.a.i)
```
- Fixed `RefTo` to always use `Action`
- Moved Diode under `io.suzaku` organization

## 1.1.0

- Added `dispatchCB` and `dispatchNow` to `ModelProxy` to provide a more explicit way of dispatching in a `Callback` or directly
- Added a Pot.fromOption convenience method (by @vpavkin)
- Circuit `subscribe` now uses `ModelRO[T]` in its listener callback so that the listener does not need to care about the type of the
model.
- Moved many methods from ModelR into its super trait ModelRO
- Support for Scala 2.12
- Updated to Scala.js 0.6.13 (for Scala 2.12 support)

## 1.0.0

### Action type safety

Major change in 1.0.0 was making dispatched actions type safe (fixing #11 in process). Action type safety is based on an implicit type class
`ActionType[A]` that must be present in scope for any valid action. Easiest way to achieve this is to utilize the provided `Action` type as a
base for all your actions. If this is not possible, for example you have no control over action classes, you need to provide an instance of
`ActionType`.

For example if you had something like,

```scala
trait MyAction

case class Increase(amount: Int) extends MyAction

case class Decrease(amount: Int) extends MyAction
```

you can change it to either

```scala
trait MyAction extends diode.Action

case class Increase(amount: Int) extends MyAction

case class Decrease(amount: Int) extends MyAction
```

or

```scala
trait MyAction

object MyAction {
  implicit object MyActionType extends ActionType[MyAction]
}

case class Increase(amount: Int) extends MyAction

case class Decrease(amount: Int) extends MyAction
```

Both variations work, but it's recommended to use the `Action` type.

This change also reflects on how action batches and empty actions are expressed. What used to be a `Seq[A]` is now an `ActionBatch` and an
empty action is indicated with `NoAction` instead of `None`. Both of these new types are subtypes of the new `Action` type.

### Connecting React components

The `ReactConnector` was updated in two ways. The first change affects how you `connect` your component and requires changes in your code. The
change was needed because the original approach caused a lot of needless unmounting and mounting of the component. In your code you must break
the connect into two parts, where the first part is executed only once creating the connecting wrapper and in the second part the wrapper is used
to connect your own component. For example

```scala
staticRoute(root, CustomerRoot) ~> render(connect(_.customerData)(proxy => Customers(proxy)))
```

should be changed to

```scala
val customerData = connect(_.customerData)
...
staticRoute(root, CustomerRoot) ~> render(customerData(proxy => Customers(proxy)))
```

If you need to specify the type for the wrapper, use `ReactConnectProxy[A]`, for example `val customerData: ReactConnectProxy[CustomerData]` in the
example above.

Another change was the addition of an optional `key` to the component created in `connect` (fixes #17).

### Other changes

- Support for silent model changes that do not trigger listeners through `ModelUpdateSilent` and `ModelUpdateSilentEffect`
- Dispatcher supports nested dispatching and queues any dispatch requests received while dispatching a previous action
- Fixed `map`, `flatMap` and `flatten` in `Pot` to return a correct type of `Pot` instead of `Empty`/`Ready`
- Added `ModelRO[S]` trait (for "read-only") to abstract reader functionality that does not need to know about the
  base model type. Can be used to replace types of `ModelR[_, S]`
- Updated to Scala.js 0.6.9

## 0.5.2
- Fixed a bug in `foldHandlers` where model changes in earlier handles were not always taken into account
- Fixed a bug in `Circuit` where subscribing to a listener while other listeners were being called resulted in that new
  subscription being ignored.

## 0.5.1
- Changed Circuit `actionHandler` type to take current model as parameter to enable chaining of handlers
- Added `composeHandlers` and `foldHandlers` to help building action handler hierarchies
- `combineHandlers` is deprecated and replaced with `composeHandlers`
- Exposed `root` model reader in the `ModelR` trait

## 0.5.0
- Introduced `FastEq` typeclass to provide suitable equality checking in various cases using `===` and `=!=` operators.
- `PotCollection` fetching is always asynchronous to prevent nasty corner cases
- Circuit subscription requires a `ModelR` instead of a simple function
- The `model` in Circuit is now private, override `initialModel` method to set the initial value
- Updated to Scala.js 0.6.7
- DevTools updated to scalajs-dom 0.9.0 (backwards incompatible change in accessing `indexedDB`)

## 0.4.0
- Split Diode into `diode-core` and `diode-data` modules as the core functionality is quite stable but `diode-data`
  (`Pot` stuff) is still changing quite rapidly.
- Added `AsyncAction` which is a more general base for `PotAction`.
- Simplified `Pot` by moving everything RetryPolicy related into specific `AsyncActionRetriable`.

## 0.3.0
- Added virtual collections (`PotCollection`) to support lazy loading of data.
- Added `RefTo` for referencing data elsewhere in the model.
- Added `map` and `flatMap` to access model values inside containers (such as `Option`) while maintaining reference
  equality.
- Added `zip` to combine two readers while maintaining reference equality.
- Added an action processor for persisting application state.
- Moved `Pot` and related classes from `diode.util` to `diode.data` package.

## 0.2.0
- Upgraded `Effect`s to be real class(es) instead of just type alias for easier composition etc.
- Added animation example using `requestAnimationFrame`.
- Added TodoMVC example using React.

## 0.1.0
- Initial version.

