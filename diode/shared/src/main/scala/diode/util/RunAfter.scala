package diode.util

import scala.annotation.implicitNotFound
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

@implicitNotFound("""Cannot find an implicit RunAfter. You might pass
an (implicit runner: RunAfter) parameter to your method
or import diode.Implicits.runAfterImpl""")
trait RunAfter {
  def runAfter[A](delay: FiniteDuration)(f: => A): Future[A]
}
