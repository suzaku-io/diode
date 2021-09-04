package diode.util

import java.util.concurrent.TimeUnit

import diode.Effect

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
  * Define a policy for retrying
  */
trait RetryPolicy {

  /**
    * Checks if retry should be attempted.
    *
    * @param reason
    *   Reason for failure leading to this retry. Used for filtering.
    * @return
    */
  def canRetry(reason: Throwable): Boolean

  /**
    * Retries an effect. Returns `Left` if retry is not possible and `Right[(RetryPolicy, Effects)]` if it is.
    *
    * @param reason
    *   Reason for failure leading to this retry. Used for filtering.
    * @param effectProvider
    *   Effect to be retried.
    * @return
    */
  def retry[T <: AnyRef](reason: Throwable, effectProvider: RetryPolicy => Effect): Either[Throwable, (RetryPolicy, Effect)]
}

object Retry {

  /**
    * Default retry policy that never retries.
    */
  case object None extends RetryPolicy {
    override def canRetry(reason: Throwable) = false

    override def retry[T <: AnyRef](reason: Throwable, effectProvider: RetryPolicy => Effect) =
      Left(reason)
  }

  private def always(t: Throwable) = true

  /**
    * Retries a max of `retriesLeft` times immediately following a failure.
    *
    * @param retriesLeft
    *   Number of retries
    * @param filter
    *   A filter to check if the cause of failure should prevent retrying.
    */
  case class Immediate(retriesLeft: Int, filter: Throwable => Boolean = always) extends RetryPolicy {
    override def canRetry(reason: Throwable) =
      retriesLeft > 0 && filter(reason)

    override def retry[T <: AnyRef](reason: Throwable, effectProvider: RetryPolicy => Effect) = {
      if (canRetry(reason)) {
        val nextPolicy = Immediate(retriesLeft - 1, filter)
        Right((nextPolicy, effectProvider(nextPolicy)))
      } else {
        Left(reason)
      }
    }
  }

  /**
    * Provides an exponential backoff algorithm for retrying.
    *
    * @param retriesLeft
    *   Number of retries
    * @param delay
    *   Delay after failure before trying again. Grows on each retry to `prevDelay * exp`
    * @param exp
    *   Exponential growth factor for delay. Default is 2.0 leading to delay doubling on every retry.
    * @param filter
    *   A filter to check if the cause of failure should prevent retrying.
    */
  case class Backoff(
      retriesLeft: Int,
      delay: FiniteDuration,
      exp: Double = 2.0,
      filter: Throwable => Boolean = always
  )(implicit runner: RunAfter, ec: ExecutionContext)
      extends RetryPolicy {
    override def canRetry(reason: Throwable) =
      retriesLeft > 0 && filter(reason)

    override def retry[T <: AnyRef](reason: Throwable, effectProvider: RetryPolicy => Effect) = {
      if (canRetry(reason)) {
        // calculate next delay time
        val nextDelay  = (delay.toUnit(TimeUnit.MILLISECONDS) * exp).millis
        val nextPolicy = Backoff(retriesLeft - 1, nextDelay, exp, filter)
        // wrap effect into a delayed effect
        Right((nextPolicy, effectProvider(nextPolicy).after(delay)))
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
