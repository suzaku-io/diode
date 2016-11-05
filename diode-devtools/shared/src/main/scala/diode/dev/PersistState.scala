package diode.dev

import diode._

import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Provides an action processor for saving and loading application state.
  * Needs to be extended with an actual implementation for pickling and save/load.
  */
abstract class PersistState[M <: AnyRef, P] extends ActionProcessor[M] {
  import PersistState._

  // Function to serialize the application model into internal representation
  def pickle(model: M): P

  // Function to deserialize the application model from internal representation
  def unpickle(pickled: P): M

  // Function to save the serialized model
  def save(id: String, pickled: P): Unit

  // Function to load the serialized model
  def load(id: String): Future[P]

  // internal action dispatched once loading is completed
  private case class Loaded(newModel: M) extends Action

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
}

object PersistState {

  sealed trait PersistAction extends Action

  // define external actions
  final case class Save(id: String) extends PersistAction

  final case class Load(id: String) extends PersistAction
}
