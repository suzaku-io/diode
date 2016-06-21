# Stateful Actions

After you start using `Pot` (for medicinal purposes!) in your application, you will soon realize you have a lot of actions covering various aspects of the
loading process.

```scala
case class LoadTodos extends Action
case class UpdateTodos extends Action
case class LoadTodosFailed extends Action
case class LoadTodosPending extends Action
```

Because this is such a common pattern, wouldn't it be nice to abstract this functionality into a trait and reuse it for all your `Pot` actions?

## `PotAction`

The `PotAction` trait simplifies creation of stateful actions for `Pot` data. To use it, simply define a case class extending `PotAction` and define the `next`
function to build a new instance of your action. The `PotAction` trait extends Diode's `Action` trait, so all your classes using it will automatically be
valid for dispatching.

```scala
case class UpdateTodos(potResult: Pot[Todos] = Empty) extends PotAction[Todos, UpdateTodos] {
  def next(newResult: Pot[Todos]) = UpdateTodos(newResult)
}
```

Now you can write an action handler managing the different states of your `UpdateTodos`.

```scala
override def handle = {
  case action: UpdateTodos =>
    val updateEffect = action.effect(loadTodos())(todos => Todos(todos))
    action.handle {
      case PotEmpty =>
        updated(value.pending(), updateEffect)
      case PotPending =>
        noChange
      case PotReady =>
        updated(action.value)
      case PotUnavailable =>
        updated(value.unavailable())
      case PotFailed =>
        updated(value.fail(ex))
    }
}
```

We use `action.effect` to convert a normal function call returning a future into an `Effect` returning a new `PotAction`. The second parameter is a mapping
function to transform the result of the future into the correct type for our action.

Looking at the state handler it seems that there is nothing specific to our `UpdateTodos` action, so perhaps we can further reduce code size?

### Common Handlers

The state management of your `PotAction`s is typically identical, so it makes sense to extract it into a common handler. `PotAction` supports handling the
action via an external handler function through `handleWith`.

```scala
val updateEffect = action.effect(loadTodos())(todos => Todos(todos))

override def handle = {
  case action: UpdateTodos =>
    action.handleWith(this, updateEffect)(PotAction.handler())
}
```

Here we use a predefined handler from the `PotAction` object. Note how the `updateEffect` definition can be moved outside the handling function because it's
immutable.

### Notifications while Pending

If a `Pot` stays in pending state for too long, you often want to notify the user by showing something in the user interface. But by default the model is not
updated while an operation is running, so we need to do that ourselves. Easiest way to do that is to use a delayed effect that refreshes model state at a
given interval. You can create a second, delayed effect with `Effect.action(action.pending).after(someTime)` and combine it with the normal update effect using
the `+` operator.

```scala
case PotEmpty =>
  updated(value.pending(), updateEffect + Effect.action(action.pending).after(someTime))
```

Now after `someTime` the effect completes and dispatches the `action.pending` action which is handled below:

```scala
case PotPending =>
  if(value.isPending)
    updated(value.pending(), Effect.action(action.pending).after(someTime))
  else
    noChange
```

First we check if the `Pot` is still pending (it hasn't transitioned into failed or ready state in the meanwhile) and if so, we "update" the value to pending
state (which makes a copy of the `Pot`, creating a new reference, triggering an update in the view) and resubmit a delayed effect. This continues while the
`PotAction` is in pending state, updating the model at a steady interval.

The common handlers also support sending updates while pending. You just need to provide the interval time to the handler

```scala
import scala.concurrent.duration._

override def handle = {
  case action: UpdateTodos =>
    action.handleWith(this, updateEffect)(PotAction.handler(400.milliseconds))
}
```

In the view you can check how long the `Pot` has been pending by calling `duration()` and act accordingly.

```scala
if (pot.isPending) {
  val duration = pot.asInstanceOf[PendingBase].duration()
  ...
```

## `AsyncAction`

For actions not involving `Pot` data you can use `AsyncAction`. It provides the same functionality but the state is separated from the value and the type of
value is `Try[A]` to indicate success/failure.

```scala
case class UpdateTodos(
  state: PotState = PotState.Empty, 
  result: Try[Todos] = Failure(new AsyncAction.PendingException)
) extends AsyncAction[Todos, UpdateTodos] {
  def next(newState: PotState, newResult: Try[Todos]): P = UpdateTodos(newState, newResult)
}
```

## Retries

A common pattern with async data is to retry failed operations a few times. `AsyncActionRetriable` and `PotActionRetriable` support this pattern by providing
a retry policy. The retry policy resides in the action and it's updated on every retry. Therefore you need to pass it forward in the `next` method.

```scala
case class UpdateTodos(result: Pot[Todos] = Empty, retryPolicy: RetryPolicy = Retry.None) 
  extends PotActionRetriable[Todos, UpdateTodos] {
  def next(newResult: Pot[Todos], newRetryPolicy: RetryPolicy) = UpdateTodos(newResult, newRetryPolicy)
}
```

When a failure is encountered, the retry policy is consulted on what to do next:

```scala
// create an effect function that takes retry policy
val updateEffect = action.effectWithRetry(loadTodos())(todos => Todos(todos))

case PotFailed =>
  // extract exception from action and call retryPolicy
  action.retryPolicy.retry(action.result.failed.get, updateEffect) match {
    case Right((_, retryEffect)) =>
      effectOnly(retryEffect)
    case Left(ex) =>
      updated(value.fail(ex))
  }
```

Common retry policies `Immediate` and `Backoff` are available in the `Retry` object, but feel free to roll your own.
