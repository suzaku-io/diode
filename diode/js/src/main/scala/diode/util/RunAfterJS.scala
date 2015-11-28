package diode.util

import scala.concurrent.Promise
import scala.concurrent.duration.FiniteDuration
import scala.scalajs.js.timers._

class RunAfterJS extends RunAfter {
  override def runAfter[A](delay: FiniteDuration)(f: => A) = {
    val p = Promise[A]()
    setTimeout(delay)(p.success(f))
    p.future
  }
}
