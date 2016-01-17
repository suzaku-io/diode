# Async Model Data

In real applications you quite often have to deal with model data that is asynchronously loaded or updated. Usual approach would be to use `Option[A]` to
indicate whether data is there or not, but then you cannot deal with error situations. Using `Either[ErrorType, Option[A]]` or `Try[Option[A]]` gives your some
control over errors, but if you consider refreshing old data and getting an error back, what should the result be? Not to mention knowing how long the async
operation has been running and notifying the user when it has been delayed for too long. Clearly the existing data containers are not enough for this.

## Potential Data

<img src="../images/pot-states.png" style="float: right; padding: 10px";>
A `Pot[+A]` represents _potential_ data that may exist in seven different states as seen in the picture on the right. `Pot` provides you with the same interface as
`Option` or `Try`, making it very easy to use in your existing application code. Things like _for comprehension_, `getOrElse` or even `recover` are supported.
Like `Option`, `Pot` is immutable and you have to create a new `Pot` when the state or content changes.

`Pot` usually starts as an `Empty` (option `None`) or `Ready` (option `Some`), depending on whether you have the data available or not. You can use the `empty`
method to create a correctly typed `Empty`.

```scala
var myData = Pot.empty[String] // myData is now Empty
```

When you make an async call to fetch data, change the state to `Pending` with the help of `pending` function.

```scala
myData = myData.pending() // myData is now Pending
```

Assuming you get a successful response from the server, you can change the state to `Ready`.

```scala
myData = myData.ready(data) // myData is now Ready(data)
```

In case something went wrong, you can store the exception in the `Failed` state.

```scala
myData = myData.fail(exception) // myData is now Failed(exception)
```

When you are ready to refresh your `Ready` data, you again call `pending` but this time it results in a `PendingStale` indicating that old data is still there to be
used while new data is fetched.

```scala
myData = myData.pending() // myData is now PendingStale(data)
```

In case the async call gets new results, the state goes to `Ready(newData)` but if something goes wrong, you'll end up with `FailedStale` containing the
old data.

```scala
myData = myData.fail(exception) // myData is now FailedStale(data, exception)
```

If the data you are trying to fetch does not exist, you can use `Unavailable` to indicate that.

```scala
myData = myData.unavailable() // myData is now Unavailable
```

### Usage

`Pot` exposes its state via following functions.

```scala
def isEmpty: Boolean
def nonEmpty = !isEmpty
def isPending: Boolean
def isStale: Boolean
def isFailed: Boolean
def isReady: Boolean
def isUnavailable: Boolean
def state: PotState
```

You can query specific state via `isX` functions, or use `state` for matching. Note that `isEmpty` means there is no data in the `Pot`, so states `Unavailable`,
`Pending` and `Failed`, in addition to `Empty`, all return `true`.

Otherwise a `Pot` works like an `Option` (`Empty`, `Unavailable`, `Pending` and `Failed` work like `None`).

### Pending Time

The pending states of `Pot` store a time stamp when created. You can access this through `startTime`, or you can directly get the duration of the operation
with `duration(currentTime)`. This is useful in an user interface, where you'll want to show an indicator if the operation is taking too long. If
an operation fails and you retry it, the `startTime` will remain the same, giving you the total duration of the retried operations.
