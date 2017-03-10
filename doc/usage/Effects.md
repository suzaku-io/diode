# Async Effects

<img src="../images/architecture-effect.png" style="float: right; padding: 10px">
Action handlers in Diode are _pure functions_, meaning they can just do computation based on the action and the current
model. Side effects, such as making a request to the server, are not allowed. This restriction is mainly to make testing
of handlers easier, when they behave in a predictable fashion.

So how do you then perform async requests in a Diode application? In Redux this is solved by placing the async calls
into _action creators_, functions that return actions. While this keeps action handlers pure, it makes the application
design fragmented. In Diode we follow in the footsteps of Elm by wrapping any side effects into `Effect`s and returning
them from the action handler with the updated model.

## What is an Effect?

In an Effect we _describe_ a computation instead of directly _executing_ it. It's quite similar to the `IO` concept in
Scalaz/Haskell, containing a function that returns a `Future[AnyRef]`.

The action returned by the `Future` is automatically dispatched by the Circuit. If your effect doesn't need anything
dispatched, return a `NoAction`. The same type checking rules apply to Effects as to normal action dispatching, so the type returned by your effect must have
a valid `ActionType` type class implicitly available.

### Using Effects

To create an effect you need a function that runs something asynchronously, for example performing an Ajax call.
 
```scala
import org.scalajs.dom.ext.Ajax

case class NewMessages(msgs: String) extends Action

def loadMessagesEffect(user: String) = 
  Effect(Ajax.get(s"/user/messages?id=$user").map(r => NewMessages(r.responseText)))
```

Here the `loadMessagesEffect` doesn't actually execute the Ajax call immediately, but just provides an effect to do so.
Once the `Ajax` future does complete, the result is mapped into a `NewMessages` action.

To return the effects alongside the new model, use one of the helper functions provided by `ActionHandler`.

```scala
case class Messages(msgs: String, loadTime: Int)

val messageHandler = new ActionHandler(zoomTo(_.messages)) {
  override def handle = {
    case LoadMessages(user) =>
      updated(value.copy(loadTime = 0), loadMessagesEffect(user))
    case NewMessages(msgs) =>
      updated(Messages(msgs, -1))
  }
}
```

If you have no state change, use `effectOnly` instead of `updated`.
 
### Combining effects

If you want to combine multiple effects into one, join them with the `>>`, `<<` and `+` operators.

```scala
val serialAB = Effect(a) >> Effect(b)  // b is run after a completes
val serialBA = Effect(a) << Effect(b)  // a is run after b completes
val parallelAB = Effect(a) + Effect(b) // a and b are run in parallel
```

For example we might want to get periodic notifications (using `after`) while the messages are being loaded, to update
the UI accordingly.

```scala
    case LoadMessages(user) =>
      updated(value.copy(loadTime = 0), 
        loadMessagesEffect(user) + Effect.action(StillLoading).after(500.millis))
    case NewMessages(msgs) =>
      updated(Messages(msgs, -1))
    case StillLoading =>
      if(value.loadTime != -1)
        updated(value.copy(loadTime = value.loadTime + 500),  
          Effect.action(StillLoading).after(500.millis)) 
      else
        noChange
```

Note how we need to update model when handling `StillLoading` because otherwise no views would be informed about the
change.

For a more elaborate multi-state action management, take a look at [`PotActions`](../advanced/PotActions.md)
