# Actions

<img src="../images/architecture-action.png" style="float: right; padding: 10px";>
Actions hold information on how to update the model and they are the _only_ way to make these updates. Basically the current model is a result of all previous
actions, much like in [event sourcing](http://martinfowler.com/eaaDev/EventSourcing.html).

In Diode actions can be anything (extending `AnyRef`) but typically case classes are used for easy pattern matching. 