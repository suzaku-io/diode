package diode

import diode.util.RunAfter

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

sealed trait ActionResult[+M]

sealed trait ModelUpdated[+M] extends ActionResult[M] {
  def newValue: M
}

object ActionResult {

  case object NoChange extends ActionResult[Nothing]

  case class ModelUpdate[M](newValue: M) extends ModelUpdated[M]

  case class ModelUpdateEffect[M, A <: AnyRef](newValue: M, effects: Effects) extends ModelUpdated[M]
}

abstract class ActionHandler[M, T](val modelRW: ModelRW[M, T]) {

  import ActionResult._

  def handle: PartialFunction[AnyRef, ActionResult[M]]

  def value: T = modelRW.value

  def updated(newValue: T): ActionResult[M] =
    ModelUpdate(modelRW.updated(newValue))

  def updated[A <: AnyRef](newValue: T, effects: Effects): ActionResult[M] =
    ModelUpdateEffect(modelRW.updated(newValue), effects)

  def noChange: ActionResult[M] =
    NoChange

  def effectOnly[A <: AnyRef](effects: Effects): ActionResult[M] =
    ModelUpdateEffect(modelRW.updated(value), effects)

  def runAfter[A <: AnyRef](delay: FiniteDuration)(f: => A)(implicit runner: RunAfter, ec: ExecutionContext): Effects =
    Effects(runner.runAfter(delay)(f))
}
