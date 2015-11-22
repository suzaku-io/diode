package diode.util

import diode._

import scala.concurrent.{ExecutionContext, Future}

trait PotAction[A, T <: PotAction[A, T]] {
  def model: Pot[A]
  def next(value: Pot[A]): T

  def state = model.state

  def handle[M](pf: PartialFunction[PotState, ActionResult[M]]) = pf(state)

  def handleWith[M, S](handler: ActionHandler[M, S], loader: () => Future[T])
    (f: (PotAction[A, T], ActionHandler[M, S], PotState, () => Future[T]) => ActionResult[M]) = f(this, handler, state, loader)

  def pending = next(Pending())

  def ready(a: A) = next(Ready(a))

  def failed(ex: Throwable) = next(Failed(ex))

  def from[B](f: Future[B])(success: B => A, failure: Throwable => Throwable = identity)(implicit ec: ExecutionContext): () => Future[T] =
    () => f.map(x => ready(success(x))).recover { case e: Throwable => failed(failure(e)) }
}

object PotAction {
  def handler[A, M, T <: PotAction[A, T]](retries: Int = 0, progressDelta: Int = 0)(implicit runner: RunAfter, ec: ExecutionContext) =
    (action: PotAction[A, T], handler: ActionHandler[M, Pot[A]], state: PotState, updateFunc: () => Future[T]) => {
      import PotState._
      import handler._
      state match {
        case PotEmpty =>
          if (progressDelta > 0) {
            updatePar(value.pending(retries), updateFunc, runAfter(progressDelta)(action.pending))
          } else {
            update(value.pending(retries), updateFunc)
          }
        case PotPending =>
          if (value.isPending && progressDelta > 0) {
            update(value.pending(), runAfter(progressDelta)(action.pending))
          } else {
            noChange
          }
        case PotReady =>
          update(action.model)
        case PotFailed =>
          if (value.canRetry) {
            update(value.retry, updateFunc)
          } else {
            update(action.model)
          }
      }
    }
}