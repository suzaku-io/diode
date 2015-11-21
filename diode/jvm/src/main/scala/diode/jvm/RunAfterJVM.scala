package diode.jvm

import diode.RunAfter

import scala.concurrent.Promise
import java.util.concurrent._

class RunAfterJVM extends RunAfter {
  override def runAfter[A](millis: Int)(f: => A) = {
    val p = Promise[A]()
    val task = new Runnable {
      def run() = p.success(f)
    }
    RunAfterJVM.executor.schedule(task, millis, TimeUnit.MILLISECONDS)
    p.future
  }
}

object RunAfterJVM {
  private val executor = new ScheduledThreadPoolExecutor(1)
}
