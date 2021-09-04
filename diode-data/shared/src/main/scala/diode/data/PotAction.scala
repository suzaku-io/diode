package diode.data

import diode._
import diode.util._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/**
  * An async action handler for `Pot` values. Combines the state and result of `AsyncAction` into a single `Pot` value.
  *
  * @see
  *   [[AsyncAction]]
  * @tparam A
  *   Type of action result
  * @tparam P
  *   Type of the actual action class
  */
trait PotAction[A, P <: PotAction[A, P]] extends AsyncAction[A, P] {
  def potResult: Pot[A]

  def next(newResult: Pot[A]): P

  override def next(newState: PotState, newValue: Try[A]): P = (newState, newValue) match {
    case (PotState.PotEmpty, _)               => next(Empty)
    case (PotState.PotUnavailable, _)         => next(Unavailable)
    case (PotState.PotPending, _)             => next(potResult.pending())
    case (PotState.PotFailed, Failure(ex))    => next(potResult.fail(ex))
    case (PotState.PotReady, Success(result)) => next(potResult.ready(result))
    case _ =>
      throw new IllegalStateException(s"PotAction is trying to enter an invalid state ($newState)")
  }

  override def result = potResult.state match {
    case PotState.PotEmpty | PotState.PotPending =>
      Failure(new AsyncAction.PendingException)
    case PotState.PotFailed =>
      Failure(potResult.exceptionOption.get)
    case PotState.PotUnavailable =>
      Failure(new AsyncAction.UnavailableException)
    case PotState.PotReady =>
      Success(potResult.get)
  }

  override def state = potResult.state
}

/**
  * A retriable async action handler for `Pot` values. Combines the state and result of `AsyncAction` into a single `Pot`
  * value.
  *
  * @tparam A
  *   Type of action result
  * @tparam P
  *   Type of the actual action class
  */
trait PotActionRetriable[A, P <: PotActionRetriable[A, P]] extends PotAction[A, P] with AsyncActionRetriable[A, P] {
  def next(newValue: Pot[A], newRetryPolicy: RetryPolicy): P

  override def next(newValue: Pot[A]): P =
    next(newValue, retryPolicy)

  override def next(newState: PotState, newValue: Try[A], newRetryPolicy: RetryPolicy): P = (newState, newValue) match {
    case (PotState.PotEmpty, _)               => next(Empty, newRetryPolicy)
    case (PotState.PotUnavailable, _)         => next(Unavailable, newRetryPolicy)
    case (PotState.PotPending, _)             => next(potResult.pending(), newRetryPolicy)
    case (PotState.PotFailed, Failure(ex))    => next(potResult.fail(ex), newRetryPolicy)
    case (PotState.PotReady, Success(result)) => next(potResult.ready(result), newRetryPolicy)
    case _ =>
      throw new IllegalStateException(s"PotAction is trying to enter an invalid state ($newState)")
  }
}

object PotAction {

  /**
    * Provides state machine handling of a `Pot` action
    */
  def handler[A, M, P <: PotAction[A, P]]()(implicit
      ec: ExecutionContext
  ): (PotAction[A, P], ActionHandler[M, Pot[A]], Effect) => ActionResult[M] =
    handler(Duration.Zero)(diode.Implicits.runAfterImpl, ec)

  /**
    * Provides state machine handling of a `Pot` action, with progress updates.
    *
    * @param progressInterval
    *   Interval at which to update progress
    */
  def handler[A, M, P <: PotAction[A, P]](progressInterval: FiniteDuration)(implicit
      runner: RunAfter,
      ec: ExecutionContext
  ) = { (action: PotAction[A, P], handler: ActionHandler[M, Pot[A]], updateEffect: Effect) =>
    {
      import PotState._
      import handler._
      action.state match {
        case PotEmpty =>
          if (progressInterval > Duration.Zero)
            updated(value.pending(), updateEffect + Effect.action(action.pending).after(progressInterval))
          else
            updated(value.pending(), updateEffect)
        case PotPending =>
          if (value.isPending && progressInterval > Duration.Zero)
            updated(value.pending(), Effect.action(action.pending).after(progressInterval))
          else
            noChange
        case PotUnavailable =>
          updated(value.unavailable())
        case PotReady =>
          updated(action.potResult)
        case PotFailed =>
          val ex = action.result.failed.get
          updated(value.fail(ex))
      }
    }
  }
}

object PotActionRetriable {

  /**
    * Provides state machine handling of a retriable `Pot` action
    */
  def handler[A, M, P <: PotActionRetriable[A, P]]()(implicit
      ec: ExecutionContext
  ): (PotActionRetriable[A, P], ActionHandler[M, Pot[A]], RetryPolicy => Effect) => ActionResult[M] =
    handler(Duration.Zero)(diode.Implicits.runAfterImpl, ec)

  /**
    * Provides state machine handling of a retriable `Pot` action, with progress updates.
    *
    * @param progressInterval
    *   Interval at which to update progress
    */
  def handler[A, M, P <: PotActionRetriable[A, P]](progressInterval: FiniteDuration)(implicit
      runner: RunAfter,
      ec: ExecutionContext
  ) = { (action: PotActionRetriable[A, P], handler: ActionHandler[M, Pot[A]], updateEffect: RetryPolicy => Effect) =>
    {
      import PotState._
      import handler._
      action.state match {
        case PotEmpty =>
          if (progressInterval > Duration.Zero)
            updated(
              value.pending(),
              updateEffect(action.retryPolicy) + Effect.action(action.pending).after(progressInterval)
            )
          else
            updated(value.pending(), updateEffect(action.retryPolicy))

        case PotPending =>
          if (value.isPending && progressInterval > Duration.Zero)
            updated(value.pending(), Effect.action(action.pending).after(progressInterval))
          else
            noChange
        case PotUnavailable =>
          updated(value.unavailable())
        case PotReady =>
          updated(action.potResult)
        case PotFailed =>
          action.retryPolicy.retry(
            action.potResult.exceptionOption.getOrElse(new IllegalStateException("Pot is not in a failed state")),
            updateEffect
          ) match {
            case Right((_, retryEffect)) =>
              effectOnly(retryEffect)
            case Left(ex) =>
              updated(value.fail(ex))
          }
      }
    }
  }
}
