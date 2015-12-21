package diode.data

import diode._
import diode.util._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

trait PotAction[A, P <: PotAction[A, P]] {
  def value: Pot[A]
  def next(newValue: Pot[A]): P

  def state = value.state

  def handle[M](pf: PartialFunction[PotState, ActionResult[M]]) = pf(state)

  def handleWith[M, T](handler: ActionHandler[M, T], updateEffect: Effect)
    (f: (PotAction[A, P], ActionHandler[M, T], Effect) => ActionResult[M]) = f(this, handler, updateEffect)

  def pending = next(Pending())

  def ready(a: A) = next(Ready(a))

  def failed(ex: Throwable) = next(Failed(ex))

  def effect[B](f: => Future[B])(success: B => A, failure: Throwable => Throwable = identity)(implicit ec: ExecutionContext) =
    Effect(f.map(x => ready(success(x))).recover { case e: Throwable => failed(failure(e)) })
}

object PotAction {
  def handler[A, M, P <: PotAction[A, P]](retryPolicy: RetryPolicy = Retry.None)(implicit ec: ExecutionContext) =
    (action: PotAction[A, P], handler: ActionHandler[M, Pot[A]], updateEffect: Effect) => {
      import PotState._
      import handler._
      action.state match {
        case PotEmpty =>
          updated(value.pending(retryPolicy), updateEffect)
        case PotPending =>
          noChange
        case PotUnavailable =>
          updated(value.unavailable())
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

  def handler[A, M, P <: PotAction[A, P]](retryPolicy: RetryPolicy, progressDelta: FiniteDuration)(implicit runner: RunAfter, ec: ExecutionContext) = {
    require(progressDelta > Duration.Zero)

    (action: PotAction[A, P], handler: ActionHandler[M, Pot[A]], updateEffect: Effect) => {
      import PotState._
      import handler._
      action.state match {
        case PotEmpty =>
          updated(value.pending(retryPolicy), updateEffect + Effect.action(action.pending).after(progressDelta))
        case PotPending =>
          if(value.isPending)
            updated(value.pending(), Effect.action(action.pending).after(progressDelta))
          else
            noChange
        case PotUnavailable =>
          updated(value.unavailable())
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

  def mapHandler[K, V, A <: Traversable[(K, Pot[V])], M, P <: PotAction[A, P]](keys: Set[K], retryPolicy: RetryPolicy = Retry.None)
    (implicit ec: ExecutionContext) = {
    require(keys.nonEmpty)
    (action: PotAction[A, P], handler: ActionHandler[M, PotMap[K, V]], updateEffect: Effect) => {
      import PotState._
      import handler._
      // updates only those values whose key is in the `keys` list
      def updateInCollection(f: Pot[V] => Pot[V]): PotMap[K, V] = {
        value.map { (k, v) =>
          if (keys.contains(k))
            f(v)
          else
            v
        }
      }
      action.state match {
        case PotEmpty =>
          updated(updateInCollection(_.pending(retryPolicy)), updateEffect)
        case PotPending =>
          noChange
        case PotUnavailable =>
          noChange
        case PotReady =>
          updated(value.updated(action.value.get))
        case PotFailed =>
          // get one retry policy, they're all the same
          val rp = value.get(keys.head).retryPolicy
          rp.retry(action.value, updateEffect) match {
            case Right((nextPolicy, retryEffect)) =>
              updated(updateInCollection(_.pending(nextPolicy)), retryEffect)
            case Left(ex) =>
              updated(updateInCollection(_.fail(ex)))
          }
      }
    }
  }

  def vectorHandler[V, A <: Traversable[(Int, Pot[V])], M, P <: PotAction[A, P]](indices: Set[Int], retryPolicy: RetryPolicy = Retry.None)
    (implicit ec: ExecutionContext) = {
    require(indices.nonEmpty)
    (action: PotAction[A, P], handler: ActionHandler[M, PotVector[V]], updateEffect: Effect) => {
      import PotState._
      import handler._
      // updates only those values whose index is in the `indices` list
      def updateInCollection(f: Pot[V] => Pot[V]): PotVector[V] = {
        value.map { (k, v) =>
          if (indices.contains(k))
            f(v)
          else
            v
        }
      }
      action.state match {
        case PotEmpty =>
          updated(updateInCollection(_.pending(retryPolicy)), updateEffect)
        case PotPending =>
          noChange
        case PotUnavailable =>
          noChange
        case PotReady =>
          updated(value.updated(action.value.get))
        case PotFailed =>
          // get one retry policy, they're all the same
          val rp = value.get(indices.head).retryPolicy
          rp.retry(action.value, updateEffect) match {
            case Right((nextPolicy, retryEffect)) =>
              updated(updateInCollection(_.pending(nextPolicy)), retryEffect)
            case Left(ex) =>
              updated(updateInCollection(_.fail(ex)))
          }
      }
    }
  }
}
