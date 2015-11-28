package diode.util

import java.util.concurrent.TimeUnit

import diode.ActionResult.Effect

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
    * Retries an effect. Returns `Left` is retry is not possible and `Right[(RetryPolicy, Effect[T])]` if it is.
    *
    * @param reason Reason for failure leading to this retry. Used for filtering.
    * @param effect Effect to be retried.
    * @return
    */
  def retry[T <: AnyRef](reason: Throwable, effect: Effect[T]): Either[Throwable, (RetryPolicy, Effect[T])]
}

object Retry {

  /**
    * Default retry policy that never retries.
    */
  case object None extends RetryPolicy {
    override def canRetry(reason: Throwable) = false

    override def retry[T <: AnyRef](reason: Throwable, effect: Effect[T]) =
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

    override def retry[T <: AnyRef](reason: Throwable, effect: Effect[T]) = {
      if (canRetry(reason))
        Right((Immediate(retriesLeft - 1, filter), effect))
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

    override def retry[T <: AnyRef](reason: Throwable, effect: Effect[T]) = {
      if (canRetry(reason)) {
        // wrap the effect into a delayed effect
        val delayEffect = runner.effectAfter(delay)(effect)
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
