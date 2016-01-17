package diode.data

import diode.util.RetryPolicy
import diode.{ActionHandler, ActionResult, Effect}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait AsyncAction[A, P <: AsyncAction[A, P]] {
  def result: Try[A]

  def next(newState: PotState, newValue: Try[A]): P

  def state: PotState

  def handle[M](pf: PartialFunction[PotState, ActionResult[M]]) =
    pf(state)

  def handleWith[M, T](handler: ActionHandler[M, T], effect: Effect)
    (f: (this.type, ActionHandler[M, T], Effect) => ActionResult[M]): ActionResult[M] =
    f(this, handler, effect)

  def pending =
    next(PotState.PotPending, result)

  def ready(a: A) =
    next(PotState.PotReady, Success(a))

  def failed(ex: Throwable) =
    next(PotState.PotFailed, Failure(ex))

  def effect[B](f: => Future[B])(success: B => A, failure: Throwable => Throwable = identity)(implicit ec: ExecutionContext) =
    Effect(f.map(x => ready(success(x))).recover { case e: Throwable => failed(failure(e)) })
}

trait AsyncActionRetriable[A, P <: AsyncActionRetriable[A, P]] extends AsyncAction[A, P] {
  def next(newState: PotState, newValue: Try[A], newRetryPolicy: RetryPolicy): P

  def retryPolicy: RetryPolicy

  override def next(newState: PotState, newValue: Try[A]): P =
    next(newState, newValue, retryPolicy)

  def handleWith[M, T](handler: ActionHandler[M, T], effectRetry: RetryPolicy => Effect)
    (f: (this.type, ActionHandler[M, T], RetryPolicy => Effect) => ActionResult[M]): ActionResult[M] =
    f(this, handler, effectRetry)

  def failed(ex: Throwable, nextRetryPolicy: RetryPolicy = retryPolicy) =
    next(PotState.PotFailed, Failure(ex), nextRetryPolicy)

  def effectWithRetry[B](f: => Future[B])(success: B => A, failure: Throwable => Throwable = identity)(implicit ec: ExecutionContext) =
    (nextRetryPolicy: RetryPolicy) => Effect(f.map(x => ready(success(x))).recover { case e: Throwable => failed(failure(e), nextRetryPolicy) })
}

object AsyncAction {

  class PendingException extends Exception

  class UnavailableException extends Exception

  def mapHandler[K, V, A <: Traversable[(K, Pot[V])], M, P <: AsyncAction[A, P]](keys: Set[K])
    (implicit ec: ExecutionContext) = {
    require(keys.nonEmpty)
    (action: AsyncAction[A, P], handler: ActionHandler[M, PotMap[K, V]], updateEffect: Effect) => {
      import PotState._
      import handler._
      // updates/adds only those values whose key is in the `keys` set
      def updateInCollection(f: Pot[V] => Pot[V], default: Pot[V]): PotMap[K, V] = {
        // update existing values
        value.map { (k, v) =>
          if (keys.contains(k))
            f(v)
          else
            v
        } ++ (keys -- value.keySet).map(k => k -> default) // add new ones
      }
      action.state match {
        case PotEmpty =>
          updated(updateInCollection(_.pending(), Pending()), updateEffect)
        case PotPending =>
          noChange
        case PotUnavailable =>
          noChange
        case PotReady =>
          updated(value.updated(action.result.get))
        case PotFailed =>
          val ex = action.result.failed.get
          updated(updateInCollection(_.fail(ex), Failed(ex)))
      }
    }
  }

  def vectorHandler[V, A <: Traversable[(Int, Pot[V])], M, P <: AsyncAction[A, P]](indices: Set[Int])
    (implicit ec: ExecutionContext) = {
    require(indices.nonEmpty)
    (action: AsyncAction[A, P], handler: ActionHandler[M, PotVector[V]], updateEffect: Effect) => {
      import PotState._
      import handler._
      // updates/adds only those values whose index is in the `indices` set
      def updateInCollection(f: Pot[V] => Pot[V], default: Pot[V]): PotVector[V] = {
        // update existing values
        value.map { (k, v) =>
          if (indices.contains(k))
            f(v)
          else
            v
        }.updated(indices.filterNot(value.contains).map(i => i -> default)) // add new ones
      }
      action.state match {
        case PotEmpty =>
          updated(updateInCollection(_.pending(), Pending()), updateEffect)
        case PotPending =>
          noChange
        case PotUnavailable =>
          noChange
        case PotReady =>
          updated(value.updated(action.result.get))
        case PotFailed =>
          val ex = action.result.failed.get
          updated(updateInCollection(_.fail(ex), Failed(ex)))
      }
    }
  }
}

object AsyncActionRetriable {

  class PendingException extends Exception

  class UnavailableException extends Exception

  def mapHandler[K, V, A <: Traversable[(K, Pot[V])], M, P <: AsyncActionRetriable[A, P]](keys: Set[K])
    (implicit ec: ExecutionContext) = {
    require(keys.nonEmpty)
    (action: AsyncActionRetriable[A, P], handler: ActionHandler[M, PotMap[K, V]], updateEffect: RetryPolicy => Effect) => {
      import PotState._
      import handler._
      // updates/adds only those values whose key is in the `keys` set
      def updateInCollection(f: Pot[V] => Pot[V], default: Pot[V]): PotMap[K, V] = {
        // update existing values
        value.map { (k, v) =>
          if (keys.contains(k))
            f(v)
          else
            v
        } ++ (keys -- value.keySet).map(k => k -> default) // add new ones
      }
      action.state match {
        case PotEmpty =>
          updated(updateInCollection(_.pending(), Pending()), updateEffect(action.retryPolicy))
        case PotPending =>
          noChange
        case PotUnavailable =>
          noChange
        case PotReady =>
          updated(value.updated(action.result.get))
        case PotFailed =>
          action.retryPolicy.retry(action.result.failed.get, updateEffect) match {
            case Right((_, retryEffect)) =>
              updated(updateInCollection(_.pending(), Pending()), retryEffect)
            case Left(ex) =>
              updated(updateInCollection(_.fail(ex), Failed(ex)))
          }
      }
    }
  }

  def vectorHandler[V, A <: Traversable[(Int, Pot[V])], M, P <: AsyncActionRetriable[A, P]](indices: Set[Int])
    (implicit ec: ExecutionContext) = {
    require(indices.nonEmpty)
    (action: AsyncActionRetriable[A, P], handler: ActionHandler[M, PotVector[V]], updateEffect: RetryPolicy => Effect) => {
      import PotState._
      import handler._
      // updates/adds only those values whose index is in the `indices` set
      def updateInCollection(f: Pot[V] => Pot[V], default: Pot[V]): PotVector[V] = {
        // update existing values
        value.map { (k, v) =>
          if (indices.contains(k))
            f(v)
          else
            v
        }.updated(indices.filterNot(value.contains).map(i => i -> default)) // add new ones
      }
      action.state match {
        case PotEmpty =>
          updated(updateInCollection(_.pending(), Pending()), updateEffect(action.retryPolicy))
        case PotPending =>
          noChange
        case PotUnavailable =>
          noChange
        case PotReady =>
          updated(value.updated(action.result.get))
        case PotFailed =>
          action.retryPolicy.retry(action.result.failed.get, updateEffect) match {
            case Right((_, retryEffect)) =>
              updated(updateInCollection(_.pending(), Pending()), retryEffect)
            case Left(ex) =>
              updated(updateInCollection(_.fail(ex), Failed(ex)))
          }
      }
    }
  }
}
