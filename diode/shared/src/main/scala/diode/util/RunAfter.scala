package diode.util

import diode.ActionResult.Effect

import scala.annotation.implicitNotFound
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

@implicitNotFound("""Cannot find an implicit RunAfter. You might pass
an (implicit runner: RunAfter) parameter to your method
or import diode.Implicits.runAfter""")
trait RunAfter {
  def runAfter[A](delay: FiniteDuration)(f: => A): Future[A]

  def effectAfter[A <: AnyRef](delay: FiniteDuration)(f: Effect[A])(implicit ec: ExecutionContext): Effect[A]
}
