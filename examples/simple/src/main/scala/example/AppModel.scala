package example

import diode._

// Define the root of our application model
case class RootModel(counter: Int)

// Define actions
case class Increase(amount: Int)

case class Decrease(amount: Int)

case object Reset

/**
  * AppModel provides the actual instance of the `RootModel` and all the action
  * handlers we need. Everything else comes from the `Circuit`
  */
object AppModel extends Circuit[RootModel] {
  // define initial value for the application model
  var model = RootModel(0)

  // zoom into the model, providing access only to the
  val counterHandler = new ActionHandler(zoomRW(_.counter)((m, v) => m.copy(counter = v))) {
    override def handle = {
      case Increase(a) => update(value + a)
      case Decrease(a) => update(value - a)
      case Reset => update(0)
    }
  }
  val actionHandler = combineHandlers(counterHandler)
  /*
    // without the ActionHandler class, we would define the handler like this
    val actionHandler: PartialFunction[AnyRef, ActionResult[RootModel]] = {
      case Increase(a) => ModelUpdate(model.copy(counter = model.counter + a))
      case Decrease(a) => ModelUpdate(model.copy(counter = model.counter - a))
      case Reset => ModelUpdate(model.copy(counter = 0))
    }
  */
}
