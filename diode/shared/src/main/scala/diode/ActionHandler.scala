package diode

import scala.concurrent.{ExecutionContext, Future}

sealed trait ActionResult[M]

object ActionResult {

  case class ModelUpdate[M](newValue: M) extends ActionResult[M]

  case class ModelUpdateEffect[M, A <: AnyRef](newValue: M, effects: Seq[Future[A]], ec: ExecutionContext) extends ActionResult[M]

  case class ModelUpdateEffectPar[M, A <: AnyRef](newValue: M, effects: Seq[Future[A]], ec: ExecutionContext) extends ActionResult[M]

}

trait RunAfter {
  def runAfter[A](millis: Int)(f: => A): Future[A]
}

abstract class ActionHandler[M, T](modelRW: ModelRW[M, T]) {

  import ActionResult._

  def handle: PartialFunction[AnyRef, ActionResult[M]]

  def value: T = modelRW.value

  def update(newValue: T): ActionResult[M] = ModelUpdate(modelRW.update(newValue))

  def update[A <: AnyRef](newValue: T, effects: Future[A]*)(implicit ec: ExecutionContext): ActionResult[M] =
    ModelUpdateEffect(modelRW.update(newValue), effects, ec)

  def updatePar[A <: AnyRef](newValue: T, effects: Future[A]*)(implicit ec: ExecutionContext): ActionResult[M] =
    ModelUpdateEffectPar(modelRW.update(newValue), effects, ec)

  def noChange: ActionResult[M] = ModelUpdate(modelRW.update(value))

  def effectOnly[A <: AnyRef](effects: Future[A]*)(implicit ec: ExecutionContext): ActionResult[M] =
    ModelUpdateEffect(modelRW.update(value), effects, ec)

  def runAfter[A <: AnyRef](millis: Int)(f: => A)(implicit runner: RunAfter) = runner.runAfter(millis)(f)
}
