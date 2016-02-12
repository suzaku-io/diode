package diode

import diode.util.RunAfter

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

sealed trait ActionResult[+M]

sealed trait ModelUpdated[+M] extends ActionResult[M] {
  def newValue: M
}

object ActionResult {

  case object NoChange extends ActionResult[Nothing]

  final case class ModelUpdate[M](newValue: M) extends ModelUpdated[M]

  final case class EffectOnly(effects: Effect) extends ActionResult[Nothing]

  final case class ModelUpdateEffect[M](newValue: M, effects: Effect) extends ModelUpdated[M]

}

/**
  * Base class for all action handlers.
  *
  * @param modelRW Model reader/writer for the actions this handler processes.
  */
abstract class ActionHandler[M, T](val modelRW: ModelRW[M, T]) {

  import ActionResult._

  /**
    * Override this function to handle dispatched actions.
    */
  def handle: PartialFunction[AnyRef, ActionResult[M]]

  /**
    * Helper function that returns the current value from the model.
    */
  def value: T = modelRW.value

  /**
    * Helper function to create a `ModelUpdate` result from a new value.
    *
    * @param newValue
    * @return
    */
  def updated(newValue: T): ActionResult[M] =
    ModelUpdate(modelRW.updated(newValue))

  /**
    * Helper function to create a `ModelUpdateEffect` result from a new value and an effect.
    *
    * @param newValue
    * @param effect
    * @tparam A
    * @return
    */
  def updated[A <: AnyRef](newValue: T, effect: Effect): ActionResult[M] =
    ModelUpdateEffect(modelRW.updated(newValue), effect)

  /**
    * Helper function when the action does no model changes or effects.
    *
    * @return
    */
  def noChange: ActionResult[M] =
    NoChange

  /**
    * Helper function to create an `EffectOnly` result with the provided effect.
    *
    * @param effect
    * @tparam A
    * @return
    */
  def effectOnly[A <: AnyRef](effect: Effect): ActionResult[M] =
    EffectOnly(effect)

  /**
    * Helper function to create a delayed effect.
    *
    * @param delay How much to delay the effect.
    * @param f Result of the effect
    */
  def runAfter[A <: AnyRef](delay: FiniteDuration)(f: => A)(implicit runner: RunAfter, ec: ExecutionContext): Effect =
    Effect(runner.runAfter(delay)(f))
}
