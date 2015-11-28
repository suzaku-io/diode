# Stateful Actions

After you start using `Pot` (for medicinal purposes!) in your application, you will soon realize you have a lot of actions covering various aspects of the
loading process.

```scala
case class LoadTodos
case class UpdateTodos
case class LoadTodosFailed
case class LoadTodosPending
```

Because this is such a common pattern, wouldn't it be nice to abstract this functionality into a trait and reuse it for all your `Pot` actions?

## `PotAction`

The `PotAction` trait simplifies creation of stateful actions for `Pot` data. To use it, simply define a case class extending `PotAction` and define the `next`
function to build a new instance of your action.

```scala
case class UpdateTodos(value: Pot[Todos] = Empty) extends PotAction[Todos, UpdateTodos] {
  def next(newValue: Pot[Todos]) = UpdateTodos(newValue)
}
```

Now you can write an action handler managing the different states of your `UpdateTodos`.

```scala
override def handle = {
  case action: UpdateTodos =>
    val updateF = action.effect(loadTodos())(todos => Todos(todos))
    action.handle {
      case PotEmpty =>
        update(value.pending, updateF)
      case PotPending =>
        noChange
      case PotReady =>
        update(action.value)
      case PotFailed =>
        value.retryPolicy.retry(action.value.exceptionOption.get, updateEffect) match {
          case Right((nextPolicy, retryEffect)) =>
            update(value.retry(nextPolicy), retryEffect)
          case Left(ex) =>
            update(value.fail(ex))
        }
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
import scala.concurrent.duration._
override def handle = {
  case action: UpdateTodos =>
    val updateF = action.effect(loadTodos())(todos => Todos(todos))
    action.handleWith(this, updateF)(PotAction.handler(Retry(3), 100.millis))
}
```
    
Here we use a predefined handler from the `PotAction` object, which supports retries and periodic notification of pending actions.
