# Action Processors

<img src="../images/action-processors.png" style="float: right; padding: 10px";>

Sometimes you need to add a new aspect to action processing requiring access to all dispatched actions. Diode provides an extension mechanism to get in between
dispatching an action and handling it. These _action processors_ may do whatever they want with the incoming action, such as logging, modifying or cancelling
them. The processors can also intercept the return value from the action handler to, for example, capture the modified model into an undo stack.

## Usage

To define an action processor, create a class extending the `ActionProcessor[M]` trait and override the `process` function.

```scala
class LoggingProcessor[M] extends ActionProcessor[M] {
  var log = Vector.empty[(Long, String)]
  def process(dispatch: Dispatcher, action: Any, next: Any => ActionResult[M]): ActionResult[M] = {
    // log the action
    log = log :+ (System.currentTimeMillis(), action.toString)
    // call the next processor
    next(action)
  }
}
```

Add it to the processing chain with `addProcessor`

```scala
val logProcessor = new LoggingProcessor[RootModel]
AppCircuit.addProcessor(logProcessor)
```

Later you can remove a processor if you don't need it anymore. 

```scala
AppCircuit.removeProcessor(logProcessor)
```

## Use Cases

### Batched Action Processing

To achieve smooth animation in the browser you either need to use CSS transitions or run your animation logic using
[RAF](https://developer.mozilla.org/en-US/docs/Web/API/window/requestAnimationFrame) (`requestAnimationFrame`). Using RAF guarantees that your code is run at
the refresh rate of the display (typically 60Hz) delivering smooth animation. Additionally RAF provides an accurate time value to calculate your animation
transitions correctly.

In order for your application to utilize RAF you need to update model in the RAF callback and do so for all animations that are currently running. A convenient
way to achieve this is to use a _batching_ action processor that collects dispatched animation actions into a batch and runs them later in the RAF callback.

The [RAF example](../examples/README.md) provides a `RAFBatcher` implementation that does just this. It uses a special marker trait (`RAFAction`) to identify
which actions should be filtered out and batched. When such an action is encountered, it's wrapped inside a `RAFWrapper` and added to the current batch. If not
already done, a RAF callback is requested. When the callback is executed, all batched actions are dispatched and unwrapped when they enter the `RAFBatcher`'s
`process` function. The processor also dispatches a special `RAFTimeStamp` action containing accurate time for the animation actions. Within your Circuit you
should handle this action and update the model with the current timestamp.

```scala
val timestampHandler = new ActionHandler(zoomTo(_.now)) {
  override def handle = {
    case RAFTimeStamp(time) =>
      updated(time)
  }
}
```

The `RAFBatcher` also takes advantage of Circuit's feature of processing a sequence of actions in one go, calling listeners only after all the actions have
been processed. This improves performance and guarantees correct display of animation results. If your application dispatches a lot of small actions, it may
make sense to use a batching strategy even when no animations are involved.

### Persisting Application State

Diode [devtools](https://github.com/suzaku-io/diode/tree/master/diode-devtools) project contains a convenient action processor for saving and restoring
application state. This can be useful when encountering a bug in development and wanting to replicate the same application state later, after the code has been
fixed and application reloaded.

The gist of the state persisting action processor is in the `process` function:

```scala
override def process(dispatch: Dispatcher, action: Any, next: Any => ActionResult[M], currentModel: M) = {
  action match {
    case Save(id) =>
      // pickle and save
      save(id, pickle(currentModel))
      ActionResult.NoChange
    case Load(id) =>
      // perform state load and unpickling in an effect
      val effect = Effect(load(id).map(p => Loaded(unpickle(p))))
      ActionResult.EffectOnly(effect)
    case Loaded(newModel) =>
      // perform model update
      ActionResult.ModelUpdate(newModel)
    case _ =>
      next(action)
  }
}
```

It simply catches a few predefined actions for saving and loading application state and lets everything else pass through to the next processor. Because loading
happens asynchronously, it is lifted into an `Effect` and once loading is completed a `Loaded` action is dispatched. Within `Loaded` action handler the
application state is actually modified.

The [TodoMVC example](../examples/README.md) shows how to use `PersistState` action processor in an application.
