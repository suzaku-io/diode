package diode.util

import java.util.concurrent.TimeUnit

import diode.Effect
import diode.data.Pot

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
  * Define a policy for retrying
  */
trait RetryPolicy {
  /**
    * Checks if retry should be attempted.
    *
    * @param reason Reason for failure leading to this retry. Used for filtering.
    * @return
    */
  def canRetry(reason: Throwable): Boolean

  /**
    * Retries an effect. Returns `Left` is retry is not possible and `Right[(RetryPolicy, Effects)]` if it is.
    *
    * @param reason Reason for failure leading to this retry. Used for filtering.
    * @param effect Effect to be retried.
    * @return
    */
  def retry[T <: AnyRef](reason: Throwable, effect: Effect): Either[Throwable, (RetryPolicy, Effect)]

  def retry[T <: AnyRef](pot: Pot[_], effect: Effect): Either[Throwable, (RetryPolicy, Effect)] =
    retry(pot.exceptionOption.getOrElse(new IllegalStateException("Pot is not in a failed state")), effect)
}

object Retry {

  /**
    * Default retry policy that never retries.
    */
  case object None extends RetryPolicy {
    override def canRetry(reason: Throwable) = false

    override def retry[T <: AnyRef](reason: Throwable, effects: Effect) =
      Left(reason)
  }

  private def always(t: Throwable) = true

  /**
    * Retries a max of `retriesLeft` times immediately following a failure.
    *
    * @param retriesLeft Number of retries
    * @param filter A filter to check if the cause of failure should prevent retrying.
    */
  case class Immediate(retriesLeft: Int, filter: Throwable => Boolean = always) extends RetryPolicy {
    override def canRetry(reason: Throwable) =
      retriesLeft > 0 && filter(reason)

    override def retry[T <: AnyRef](reason: Throwable, effects: Effect) = {
      if (canRetry(reason))
        Right((Immediate(retriesLeft - 1, filter), effects))
      else
        Left(reason)
    }
  }

  /**
    * Provides an exponential backoff algorithm for retrying.
    *
    * @param retriesLeft Number of retries
    * @param delay Delay after failure before trying again. Grows on each retry to `prevDelay * exp`
    * @param exp Exponential growth factor for delay. Default is 2.0 leading to delay doubling on every retry.
    * @param filter A filter to check if the cause of failure should prevent retrying.
    */
  case class Backoff(
    retriesLeft: Int,
    delay: FiniteDuration,
    exp: Double = 2.0,
    filter: Throwable => Boolean = always
  )(implicit runner: RunAfter, ec: ExecutionContext) extends RetryPolicy {
    override def canRetry(reason: Throwable) =
      retriesLeft > 0 && filter(reason)

    override def retry[T <: AnyRef](reason: Throwable, effect: Effect) = {
      if (canRetry(reason)) {
        // wrap effects into a delayed effect
        val delayEffect = effect.after(delay)
        // calculate next delay time
        val nextDelay = (delay.toUnit(TimeUnit.MILLISECONDS) * exp).millis
        Right((Backoff(retriesLeft - 1, nextDelay, exp, filter), delayEffect))
      } else
        Left(reason)
    }
  }

  def apply(retries: Int) =
    Immediate(retries)

  def apply(retries: Int, delay: FiniteDuration)(implicit runner: RunAfter, ec: ExecutionContext) =
    Backoff(retries, delay)

  def apply(retries: Int, delay: FiniteDuration, exp: Double)(implicit runner: RunAfter, ec: ExecutionContext) =
    Backoff(retries, delay, exp)
}
