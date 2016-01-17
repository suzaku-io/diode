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

abstract class ActionHandler[M, T](val modelRW: ModelRW[M, T]) {

  import ActionResult._

  def handle: PartialFunction[AnyRef, ActionResult[M]]

  def value: T = modelRW.value

  def updated(newValue: T): ActionResult[M] =
    ModelUpdate(modelRW.updated(newValue))

  def updated[A <: AnyRef](newValue: T, effects: Effect): ActionResult[M] =
    ModelUpdateEffect(modelRW.updated(newValue), effects)

  def noChange: ActionResult[M] =
    NoChange

  def effectOnly[A <: AnyRef](effects: Effect): ActionResult[M] =
    EffectOnly(effects)

  def runAfter[A <: AnyRef](delay: FiniteDuration)(f: => A)(implicit runner: RunAfter, ec: ExecutionContext): Effect =
    Effect(runner.runAfter(delay)(f))
}
