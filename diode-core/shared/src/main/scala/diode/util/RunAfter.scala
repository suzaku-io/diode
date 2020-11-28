package diode.util

import java.util.concurrent.TimeUnit

import scala.annotation.implicitNotFound
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration.FiniteDuration

@implicitNotFound("""Cannot find an implicit RunAfter. You might pass
an (implicit runner: RunAfter) parameter to your method
or import diode.Implicits.runAfterImpl""")
trait RunAfter {
  def runAfter[A](delay: FiniteDuration)(f: => A): Future[A]

  def runAfter[A](delay: Int)(f: => A): Future[A] =
    runAfter(FiniteDuration(delay.toLong, TimeUnit.MILLISECONDS))(f)
}

object RunAfter {

  /**
    * A `RunAfter` implementation that never runs the given function
    */
  object infinity extends RunAfter {
    override def runAfter[A](delay: FiniteDuration)(f: => A): Future[A] = Promise[A]().future
  }

  /**
    * A `RunAfter` implementation that immediately runs the given function
    */
  object immediately extends RunAfter {
    override def runAfter[A](delay: FiniteDuration)(f: => A): Future[A] = Future.successful(f)
  }
}
