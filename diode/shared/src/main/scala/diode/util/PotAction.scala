package diode.util

import diode._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

trait PotAction[A, T <: PotAction[A, T]] {
  def value: Pot[A]
  def next(newValue: Pot[A]): T

  def state = value.state

  def handle[M](pf: PartialFunction[PotState, ActionResult[M]]) = pf(state)

  def handleWith[M](handler: ActionHandler[M, Pot[A]], updateEffect: Effects)
    (f: (PotAction[A, T], ActionHandler[M, Pot[A]], Effects) => ActionResult[M]) = f(this, handler, updateEffect)

  def pending = next(Pending())

  def ready(a: A) = next(Ready(a))

  def failed(ex: Throwable) = next(Failed(ex))

  def effect[B](f: => Future[B])(success: B => A, failure: Throwable => Throwable = identity)(implicit ec: ExecutionContext): Effects =
    () => f.map(x => ready(success(x))).recover { case e: Throwable => failed(failure(e)) }
}

object PotAction {
  def handler[A, M, T <: PotAction[A, T]](retryPolicy: RetryPolicy = Retry.None)(implicit ec: ExecutionContext) =
    (action: PotAction[A, T], handler: ActionHandler[M, Pot[A]], updateEffect: Effects) => {
      import PotState._
      import handler._
      action.state match {
        case PotEmpty =>
          updated(value.pending(retryPolicy), updateEffect)
        case PotPending =>
          noChange
        case PotReady =>
          updated(action.value)
        case PotFailed =>
          value.retryPolicy.retry(action.value, updateEffect) match {
            case Right((nextPolicy, retryEffect)) =>
              updated(value.retry(nextPolicy), retryEffect)
            case Left(ex) =>
              updated(value.fail(ex))
          }
      }
    }

  def handler[A, M, T <: PotAction[A, T]](retryPolicy: RetryPolicy, progressDelta: FiniteDuration)(implicit runner: RunAfter, ec: ExecutionContext) =
    (action: PotAction[A, T], handler: ActionHandler[M, Pot[A]], updateEffect: Effects) => {
      import PotState._
      import handler._
      action.state match {
        case PotEmpty =>
          if (progressDelta > Duration.Zero) {
            updated(value.pending(retryPolicy), updateEffect + runAfter(progressDelta)(action.pending))
          } else {
            updated(value.pending(retryPolicy), updateEffect)
          }
        case PotPending =>
          if (value.isPending && progressDelta > Duration.Zero) {
            updated(value.pending(), runAfter(progressDelta)(action.pending))
          } else {
            noChange
          }
        case PotReady =>
          updated(action.value)
        case PotFailed =>
          value.retryPolicy.retry(action.value, updateEffect) match {
            case Right((nextPolicy, retryEffect)) =>
              updated(value.retry(nextPolicy), retryEffect)
            case Left(ex) =>
              updated(value.fail(ex))
          }
      }
    }
}