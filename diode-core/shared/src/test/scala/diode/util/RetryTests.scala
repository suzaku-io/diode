package diode.util

import diode.{AnyAction, Effect}
import utest._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._
import diode.Implicits.runAfterImpl

object RetryTests extends TestSuite {
  import AnyAction._

  def tests = TestSuite {
    'Immediate - {
      val policy = Retry.Immediate(3)
      val effect = (retryPolicy: RetryPolicy) => Effect(Future("42"))
      val r      = policy.retry(new Exception, effect)
      assertMatch(r) {
        case Right((nextPolicy, newEffect)) =>
          assert(nextPolicy.asInstanceOf[Retry.Immediate].retriesLeft == 2)
          assert(newEffect == effect)
      }
    }
    'Backoff - {
      val policy = Retry.Backoff(3, 200.millis)
      val effect = (retryPolicy: RetryPolicy) => Effect(Future("42"))
      val r      = policy.retry(new Exception, effect)
      assert(r.isRight)
      val now = System.currentTimeMillis()
      // check that effect happens in the future
      r.right.get._2.toFuture.flatMap { n =>
        println(s"First retry after ${System.currentTimeMillis() - now}")
        assert(System.currentTimeMillis() - now > 150)
        // next retry
        val rr = r.right.get._1.retry(new Exception, effect)
        // check that effect happens in the future
        rr.right.get._2.toFuture.map { n =>
          println(s"Second retry after ${System.currentTimeMillis() - now}")
          assert(System.currentTimeMillis() - now > 550)
        }
      }
    }
  }
}
