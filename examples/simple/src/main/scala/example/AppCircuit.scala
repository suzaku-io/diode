package example

import diode._

// Define the root of our application model
case class RootModel(counter: Int)

// Define actions
case class Increase(amount: Int) extends Action

case class Decrease(amount: Int) extends Action

case object Reset extends Action

/**
  * AppCircuit provides the actual instance of the `RootModel` and all the action
  * handlers we need. Everything else comes from the `Circuit`
  */
object AppCircuit extends Circuit[RootModel] {
  // define initial value for the application model
  def initialModel = RootModel(0)

  // zoom into the model, providing access only to the
  val counterHandler = new ActionHandler(zoomTo(_.counter)) {
    override def handle = {
      case Increase(a) => updated(value + a)
      case Decrease(a) => updated(value - a)
      case Reset       => updated(0)
    }
  }

  override val actionHandler: HandlerFunction = counterHandler
  /*
    // without the ActionHandler class, we would define the handler like this
    override val actionHandler: HandlerFunction =
      (model, action) => action match {
        case Increase(a) => Some(ModelUpdate(model.copy(counter = model.counter + a)))
        case Decrease(a) => Some(ModelUpdate(model.copy(counter = model.counter - a)))
        case Reset => Some(ModelUpdate(model.copy(counter = 0)))
        case _ => None
      }
 */
}
