# Actions

<img src="../images/architecture-action.png" style="float: right; padding: 10px">
Actions hold information on how to update the model and they are the _only_ way to make these updates. Basically the current model is a result of all previous
actions, much like in [event sourcing](http://martinfowler.com/eaaDev/EventSourcing.html).

## Designing Actions
 
Actions are very much application specific but can generally be classified into _global_ and _targeted_ actions. A global action is something like the examples
below, always changing the same part of the model.

```scala
case class Increase(amount: Int) extends Action
case class Decrease(amount: Int) extends Action
case object Reset extends Action
```

Targeted actions, on the other hand, can change different parts of the model depending on the value of the action. For example if your data is in a tree, you
would need to know what part of the tree should be changed. One option is to have unique IDs for data and search for the ID in the action handler, but typically
it's more efficient and easier to use a _path_ to the data.

```scala
// path is defined by a sequence of identifiers
case class AddNode(path: Seq[String], node: FileNode) extends Action
case class RemoveNode(path: Seq[String]) extends Action
case class ReplaceNode(path: Seq[String], node: FileNode) extends Action
case class Select(selected: Seq[String]) extends Action
```

If you need an action to update multiple independent parts of your model, it's usually better to break it down into several consecutive actions. This way each
action handler can focus on its own part of the model and the logic is kept simple and easy to reason about. Actions are processed in the order they are
dispatched, so you can be sure your sequence of actions are processed in correct order.

## Typesafe actions

In Diode actions can be anything but typically case classes are used for easy pattern matching. By anything, we mean anything with a valid
`ActionType[A]` type class in scope. Diode uses this type class to implicitly verify that the dispatched action is actually a valid action. It is up
to you as the developer to decide what actions are valid by providing appropriate type class instances.

Typically a good way to accomplish this is to create a single base trait (say `MyAction`) for all your actions and provide an implicit
`ActionType[MyAction]` for it in the companion object.

```scala
trait MyAction

object MyAction {
  implicit object actionType extends ActionType[MyAction]
}
```

To make your life easier, Diode provides such a base trait (named `Action`) by default so you can start using it right away!

If you want to forgo type safety altogether, you can import the provided `AnyAction` implicit, which allows `Any` to be used as action. This can be used
for adapting legacy Diode code to a new version.

```scala
import diode.AnyAction._
```

## Special actions

Diode defines two special actions: `ActionBatch` and `NoAction`, both extending the `Action` trait.

Use `ActionBatch` to create a batch of actions that are dispatched in sequence without calling any model update listeners in between. This can be used as an
optimization.

`NoAction`, as its name implies, performs no action and can be used as a result in purely side-effecting Effects.
