package diode.data

import diode.util.RetryPolicy
import diode._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
  * Base trait for asynchronous actions. Provides support for handling of multi-state actions. Implementation classes must
  * implement `result`, `next` and `state` functions. If you are using `Pot`, consider using `PotAction` instead.
  *
  * Example:
  * {{{
  * case class MyAction(
  *   state: PotState = PotState.PotEmpty,
  *   result: Try[String] = Failure(new AsyncAction.PendingException)) extends AsyncAction[Int, MyAction] {
  *   def next(newState: PotState, newValue: Try[String]) = MyAction(newState, newValue)
  * }
  * }}}
  *
  * @tparam A
  *   Type of action result
  * @tparam P
  *   Type of the actual action class
  */
trait AsyncAction[A, P <: AsyncAction[A, P]] extends Action {
  implicit object PActionType extends ActionType[P]

  def state: PotState

  def result: Try[A]

  /**
    * Moves the action to the next state.
    *
    * @param newState
    *   New state for the action
    * @param newValue
    *   New value for the action
    * @return
    *   A new instance of this action with updated state and value
    */
  def next(newState: PotState, newValue: Try[A]): P

  /**
    * Handles the action using the supplied partial function matching current state.
    *
    * @param pf
    * @tparam M
    * @return
    */
  def handle[M](pf: PartialFunction[PotState, ActionResult[M]]) =
    pf(state)

  /**
    * Handles the action using an external handler function. The function is provided with instance of this `AsyncAction`,
    * the `ActionHandler` this action currently resides in and the provided update `Effect`.
    *
    * @param handler
    *   The current action handler instance.
    * @param effect
    *   Effect that performs the requested async operation.
    * @param f
    *   External handler function
    * @return
    *   An action result
    */
  def handleWith[M, T](handler: ActionHandler[M, T], effect: Effect)(
      f: (this.type, ActionHandler[M, T], Effect) => ActionResult[M]
  ): ActionResult[M] =
    f(this, handler, effect)

  /**
    * Moves this action to pending state.
    *
    * @return
    */
  def pending =
    next(PotState.PotPending, result)

  /**
    * Moves this action to ready state with a result value.
    *
    * @param a
    *   Result for the action.
    * @return
    */
  def ready(a: A) =
    next(PotState.PotReady, Success(a))

  /**
    * Moves this action to a failed state with the provided exception.
    *
    * @param ex
    *   Reason for the failure.
    * @return
    */
  def failed(ex: Throwable) =
    next(PotState.PotFailed, Failure(ex))

  /**
    * Wraps a by-name future result into an effect using success and failure transformations.
    *
    * @param f
    *   Future to wrap, passes using by-name (lazy evaluation)
    * @param success
    *   Transformation function for success case
    * @param failure
    *   Transformation function for failure case
    * @return
    *   An `Effect` for running the provided `Future`
    */
  def effect[B](f: => Future[B])(success: B => A, failure: Throwable => Throwable = identity)(implicit
      ec: ExecutionContext
  ) =
    Effect(f.map(x => ready(success(x))).recover { case e: Throwable => failed(failure(e)) })
}

/**
  * A retriable version of `AsyncAction`. Implementation must define a `retryPolicy`, which is also passed on in the `next`
  * method.
  *
  * @tparam A
  *   Type of action result
  * @tparam P
  *   Type of the actual action class
  */
trait AsyncActionRetriable[A, P <: AsyncActionRetriable[A, P]] extends AsyncAction[A, P] {
  def next(newState: PotState, newValue: Try[A], newRetryPolicy: RetryPolicy): P

  def retryPolicy: RetryPolicy

  override def next(newState: PotState, newValue: Try[A]): P =
    next(newState, newValue, retryPolicy)

  /**
    * Handles the action using an external handler function. The function is provided with instance of this `AsyncAction`,
    * the `ActionHandler` this action currently resides in and the provided update `Effect` creator function. The reason why
    * retry policy is provided to the effect creator is that in case of failure, the updated retry policy can be passed on to
    * the next effect instantiation.
    *
    * @param handler
    *   The current action handler instance.
    * @param effectRetry
    *   Function taking current `RetryPolicy` and returning an effect that performs the requested async operation.
    * @param f
    *   External handler function
    * @return
    *   An action result
    */
  def handleWith[M, T](handler: ActionHandler[M, T], effectRetry: RetryPolicy => Effect)(
      f: (this.type, ActionHandler[M, T], RetryPolicy => Effect) => ActionResult[M]
  ): ActionResult[M] =
    f(this, handler, effectRetry)

  /**
    * Moves this action to a failed state with the provided exception.
    *
    * @param ex
    *   Exception describing the reason for failure
    * @param nextRetryPolicy
    *   Updated retry policy
    * @return
    */
  def failed(ex: Throwable, nextRetryPolicy: RetryPolicy = retryPolicy) =
    next(PotState.PotFailed, Failure(ex), nextRetryPolicy)

  /**
    * Wraps a by-name future result into an effect creator function, using success and failure transformations.
    *
    * @param f
    *   Future to wrap, passes using by-name (lazy evaluation)
    * @param success
    *   Transformation function for success case
    * @param failure
    *   Transformation function for failure case
    * @return
    *   An `Effect` for running the provided `Future`
    */
  def effectWithRetry[B](
      f: => Future[B]
  )(success: B => A, failure: Throwable => Throwable = identity)(implicit ec: ExecutionContext) =
    (nextRetryPolicy: RetryPolicy) =>
      Effect(f.map(x => ready(success(x))).recover { case e: Throwable => failed(failure(e), nextRetryPolicy) })
}

object AsyncAction {

  class PendingException extends Exception

  class UnavailableException extends Exception

  /**
    * An async action handler for executing updates to a `PotMap`.
    *
    * @param keys
    *   Set of keys to update.
    * @return
    *   The handler function
    */
  def mapHandler[K, V, A <: Iterable[(K, Pot[V])], M, P <: AsyncAction[A, P]](keys: Set[K]) = {
    require(keys.nonEmpty, "AsyncAction:mapHandler - The set of keys to update can't be empty")
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

  /**
    * An async action handler for executing updates to a `PotVector`.
    *
    * @param indices
    *   Set of indices to update
    * @return
    *   The handler function
    */
  def vectorHandler[V, A <: Iterable[(Int, Pot[V])], M, P <: AsyncAction[A, P]](indices: Set[Int]) = {
    require(indices.nonEmpty, "AsyncAction:vectorHandler - The set of indices to update can't be empty")
    (action: AsyncAction[A, P], handler: ActionHandler[M, PotVector[V]], updateEffect: Effect) => {
      import PotState._
      import handler._
      // updates/adds only those values whose index is in the `indices` set
      def updateInCollection(f: Pot[V] => Pot[V], default: Pot[V]): PotVector[V] = {
        // update existing values
        value
          .map { (k, v) =>
            if (indices.contains(k))
              f(v)
            else
              v
          }
          .updated(indices.filterNot(value.contains).map(i => i -> default)) // add new ones
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

  /**
    * A retriable async action handler for executing updates to a `PotMap`.
    *
    * @param keys
    *   Set of keys to update.
    * @return
    *   The handler function
    */
  def mapHandler[K, V, A <: Iterable[(K, Pot[V])], M, P <: AsyncActionRetriable[A, P]](keys: Set[K]) = {
    require(keys.nonEmpty, "AsyncActionRetriable:mapHandler - The set of keys to update can't be empty")
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

  /**
    * A retriable async action handler for executing updates to a `PotVector`.
    *
    * @param indices
    *   Set of indices to update
    * @return
    *   The handler function
    */
  def vectorHandler[V, A <: Iterable[(Int, Pot[V])], M, P <: AsyncActionRetriable[A, P]](indices: Set[Int]) = {
    require(indices.nonEmpty, "AsyncActionRetriable:vectorHandler - The set of indices to update can't be empty")
    (action: AsyncActionRetriable[A, P], handler: ActionHandler[M, PotVector[V]], updateEffect: RetryPolicy => Effect) => {
      import PotState._
      import handler._
      // updates/adds only those values whose index is in the `indices` set
      def updateInCollection(f: Pot[V] => Pot[V], default: Pot[V]): PotVector[V] = {
        // update existing values
        value
          .map { (k, v) =>
            if (indices.contains(k))
              f(v)
            else
              v
          }
          .updated(indices.filterNot(value.contains).map(i => i -> default)) // add new ones
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
