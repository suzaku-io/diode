package diode.util

import diode.ActionResult.Effect
import diode._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

trait PotAction[A, T <: PotAction[A, T]] {
  def value: Pot[A]
  def next(newValue: Pot[A]): T

  def state = value.state

  def handle[M](pf: PartialFunction[PotState, ActionResult[M]]) = pf(state)

  def handleWith[M](handler: ActionHandler[M, Pot[A]], updateEffect: Effect[T])
    (f: (PotAction[A, T], ActionHandler[M, Pot[A]], Effect[T]) => ActionResult[M]) = f(this, handler, updateEffect)

  def pending = next(Pending())

  def ready(a: A) = next(Ready(a))

  def failed(ex: Throwable) = next(Failed(ex))

  def effect[B](f: => Future[B])(success: B => A, failure: Throwable => Throwable = identity)(implicit ec: ExecutionContext): Effect[T] =
    () => f.map(x => ready(success(x))).recover { case e: Throwable => failed(failure(e)) }
}

object PotAction {
  def handler[A, M, T <: PotAction[A, T]](retries: Int = 0)(implicit ec: ExecutionContext) =
    (action: PotAction[A, T], handler: ActionHandler[M, Pot[A]], updateEffect: Effect[T]) => {
      import PotState._
      import handler._
      action.state match {
        case PotEmpty =>
          update(value.pending(retries), updateEffect)
        case PotPending =>
          noChange
        case PotReady =>
          update(action.value)
        case PotFailed =>
          if (value.canRetry) {
            update(value.retry, updateEffect)
          } else {
            update(value.fail(action.value.exceptionOption.get))
          }
      }
    }

  def handler[A, M, T <: PotAction[A, T]](retries: Int, progressDelta: FiniteDuration)(implicit runner: RunAfter, ec: ExecutionContext) =
    (action: PotAction[A, T], handler: ActionHandler[M, Pot[A]], updateEffect: Effect[T]) => {
      import PotState._
      import handler._
      action.state match {
        case PotEmpty =>
          if (progressDelta > Duration.Zero) {
            updatePar(value.pending(retries), updateEffect, runAfter(progressDelta)(action.pending))
          } else {
            update(value.pending(retries), updateEffect)
          }
        case PotPending =>
          if (value.isPending && progressDelta > Duration.Zero) {
            update(value.pending(), runAfter(progressDelta)(action.pending))
          } else {
            noChange
          }
        case PotReady =>
          update(action.value)
        case PotFailed =>
          if (value.canRetry) {
            update(value.retry, updateEffect)
          } else {
            update(value.fail(action.value.exceptionOption.get))
          }
      }
    }
}