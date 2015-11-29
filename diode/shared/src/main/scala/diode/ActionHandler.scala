package diode

import diode.util.RunAfter

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

sealed trait ActionResult[+M]

sealed trait ModelUpdated[+M] extends ActionResult[M] {
  def newValue: M
}

object ActionResult {

  type Effect[Action <: AnyRef] = () => Future[Action]

  case object NoChange extends ActionResult[Nothing]

  case class ModelUpdate[M](newValue: M) extends ModelUpdated[M]

  case class ModelUpdateEffect[M, A <: AnyRef](newValue: M, effects: Seq[Effect[A]], ec: ExecutionContext) extends ModelUpdated[M]

  case class ModelUpdateEffectPar[M, A <: AnyRef](newValue: M, effects: Seq[Effect[A]], ec: ExecutionContext) extends ModelUpdated[M]

}

abstract class ActionHandler[M, T](val modelRW: ModelRW[M, T]) {

  import ActionResult._

  def handle: PartialFunction[AnyRef, ActionResult[M]]

  def value: T = modelRW.value

  def updated(newValue: T): ActionResult[M] =
    ModelUpdate(modelRW.updated(newValue))

  def updated[A <: AnyRef](newValue: T, effects: Effect[A]*)(implicit ec: ExecutionContext): ActionResult[M] =
    ModelUpdateEffect(modelRW.updated(newValue), effects, ec)

  def updatedPar[A <: AnyRef](newValue: T, effects: Effect[A]*)(implicit ec: ExecutionContext): ActionResult[M] =
    ModelUpdateEffectPar(modelRW.updated(newValue), effects, ec)

  def noChange: ActionResult[M] =
    NoChange

  def effectOnly[A <: AnyRef](effects: Effect[A]*)(implicit ec: ExecutionContext): ActionResult[M] =
    ModelUpdateEffect(modelRW.updated(value), effects, ec)

  def effectOnlyPar[A <: AnyRef](effects: Effect[A]*)(implicit ec: ExecutionContext): ActionResult[M] =
    ModelUpdateEffectPar(modelRW.updated(value), effects, ec)

  def runAfter[A <: AnyRef](delay: FiniteDuration)(f: => A)(implicit runner: RunAfter): Effect[A] =
    () => runner.runAfter(delay)(f)

  def effectAfter[A <: AnyRef](delay: FiniteDuration)(effect: Effect[A])(implicit runner: RunAfter, ec: ExecutionContext): Effect[A] =
    runner.effectAfter(delay)(effect)
}
