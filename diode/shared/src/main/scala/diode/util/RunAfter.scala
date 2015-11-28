package diode.util

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

trait RunAfter {
  def runAfter[A](delay: FiniteDuration)(f: => A): Future[A]
}
