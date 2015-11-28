package diode.util

import scala.concurrent.Promise
import java.util.concurrent._

import scala.concurrent.duration.FiniteDuration

class RunAfterJVM extends RunAfter {
  override def runAfter[A](delay: FiniteDuration)(f: => A) = {
    val p = Promise[A]()
    val task = new Runnable {
      def run() = p.success(f)
    }
    RunAfterJVM.executor.schedule(task, delay.toMillis, TimeUnit.MILLISECONDS)
    p.future
  }
}

object RunAfterJVM {
  private val executor = new ScheduledThreadPoolExecutor(1)
}


