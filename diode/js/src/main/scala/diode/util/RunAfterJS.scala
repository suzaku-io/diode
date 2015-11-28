package diode.util

import diode.ActionResult._

import scala.concurrent._
import scala.concurrent.duration.FiniteDuration
import scala.scalajs.js.timers._

class RunAfterJS extends RunAfter {
  override def runAfter[A](delay: FiniteDuration)(f: => A) = {
    val p = Promise[A]()
    setTimeout(delay)(p.success(f))
    p.future
  }

  def effectAfter[A <: AnyRef](delay: FiniteDuration)(f: Effect[A])(implicit ec: ExecutionContext) = {
    () => {
      val p = Promise[A]()
      setTimeout(delay)(f().map(p.success))
      p.future
    }
  }
}
