package diode.react

import diode._
import japgolly.scalajs.react.vdom._
import scala.concurrent.Promise
import scalajs.js.timers._

object ReactPot {

  implicit class potWithReact[A](val pot: Pot[A]) extends AnyVal {
    def render(f: A => TagMod): TagMod =
      if (pot.nonEmpty) f(pot.get) else EmptyTag

    def renderReady(f: A => TagMod): TagMod =
      if (pot.isReady) f(pot.get) else EmptyTag

    def renderPending(f: Int => TagMod): TagMod =
      if (pot.isPending) f(pot.asInstanceOf[PendingBase].duration()) else EmptyTag

    def renderFailed(f: Throwable => TagMod): TagMod =
      if (pot.isFailed) f(pot.asInstanceOf[FailedBase[Nothing]].exception) else EmptyTag

    def renderStale(f: A => TagMod): TagMod =
      if (pot.isStale) f(pot.get) else EmptyTag

    def renderEmpty(f: => TagMod): TagMod =
      if (pot.isEmpty) f else EmptyTag
  }

}


