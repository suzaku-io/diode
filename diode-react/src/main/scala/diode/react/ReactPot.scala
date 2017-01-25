package diode.react

import diode.data.{PendingBase, Pot}
import diode.util._
import japgolly.scalajs.react.ReactNode

object ReactPot {

  import scala.language.implicitConversions

  implicit class potWithReact[A](val pot: Pot[A]) extends AnyVal {

    /**
      * Render non-empty (ready or stale) content
      *
      * @param f Transforms Pot value into a ReactNode
      * @return
      */
    def render(f: A => ReactNode): ReactNode =
      if (pot.nonEmpty) f(pot.get) else null

    /**
      * Render content in Ready state, not including stale states
      *
      * @param f Transforms Pot value into a ReactNode
      * @return
      */
    def renderReady(f: A => ReactNode): ReactNode =
      if (pot.isReady) f(pot.get) else null

    /**
      * Render when Pot is pending
      *
      * @param f Transforms duration time into a ReactNode
      * @return
      */
    def renderPending(f: Int => ReactNode): ReactNode =
      if (pot.isPending) f(pot.asInstanceOf[PendingBase].duration()) else null

    /**
      * Render when Pot is pending with a filter on duration
      *
      * @param b Filter based on duration value
      * @param f Transforms duration time into a ReactNode
      * @return
      */
    def renderPending(b: Int => Boolean, f: Int => ReactNode): ReactNode = {
      if (pot.isPending) {
        val duration = pot.asInstanceOf[PendingBase].duration()
        if (b(duration)) f(duration) else null
      } else null
    }

    /**
      * Render when Pot has failed
      *
      * @param f Transforms an exception into a ReactNode
      * @return
      */
    def renderFailed(f: Throwable => ReactNode): ReactNode =
      pot.exceptionOption.map(f).orNull

    /**
      * Render stale content (`PendingStale` or `FailedStale`)
      *
      * @param f Transforms Pot value into a ReactNode
      * @return
      */
    def renderStale(f: A => ReactNode): ReactNode =
      if (pot.isStale) f(pot.get) else null

    /**
      * Render when Pot is empty
      *
      * @param f Returns a ReactNode
      * @return
      */
    def renderEmpty(f: => ReactNode): ReactNode =
      if (pot.isEmpty) f else null
  }

}
