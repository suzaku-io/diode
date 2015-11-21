package diode.js

import diode.RunAfter

import scala.concurrent.Promise
import scala.scalajs.js.timers._

class RunAfterJS extends RunAfter {
  override def runAfter[A](millis: Int)(f: => A) = {
    val p = Promise[A]()
    setTimeout(millis)(p.success(f))
    p.future
  }
}
