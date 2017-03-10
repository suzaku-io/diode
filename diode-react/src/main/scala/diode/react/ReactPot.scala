package diode.react

import diode.data.{PendingBase, Pot}
import japgolly.scalajs.react.vdom.VdomNode

object ReactPot {

  import scala.language.implicitConversions

  implicit class potWithReact[A](val pot: Pot[A]) extends AnyVal {

    /**
      * Render non-empty (ready or stale) content
      *
      * @param f Transforms Pot value into a VdomNode
      * @return
      */
    def render(f: A => VdomNode): VdomNode =
      if (pot.nonEmpty) f(pot.get) else null

    /**
      * Render content in Ready state, not including stale states
      *
      * @param f Transforms Pot value into a VdomNode
      * @return
      */
    def renderReady(f: A => VdomNode): VdomNode =
      if (pot.isReady) f(pot.get) else null

    /**
      * Render when Pot is pending
      *
      * @param f Transforms duration time into a VdomNode
      * @return
      */
    def renderPending(f: Int => VdomNode): VdomNode =
      if (pot.isPending) f(pot.asInstanceOf[PendingBase].duration()) else null

    /**
      * Render when Pot is pending with a filter on duration
      *
      * @param b Filter based on duration value
      * @param f Transforms duration time into a VdomNode
      * @return
      */
    def renderPending(b: Int => Boolean, f: Int => VdomNode): VdomNode = {
      if (pot.isPending) {
        val duration = pot.asInstanceOf[PendingBase].duration()
        if (b(duration)) f(duration) else null
      } else null
    }

    /**
      * Render when Pot has failed
      *
      * @param f Transforms an exception into a VdomNode
      * @return
      */
    def renderFailed(f: Throwable => VdomNode): VdomNode =
      pot.exceptionOption.map(f).orNull

    /**
      * Render stale content (`PendingStale` or `FailedStale`)
      *
      * @param f Transforms Pot value into a VdomNode
      * @return
      */
    def renderStale(f: A => VdomNode): VdomNode =
      if (pot.isStale) f(pot.get) else null

    /**
      * Render when Pot is empty
      *
      * @param f Returns a VdomNode
      * @return
      */
    def renderEmpty(f: => VdomNode): VdomNode =
      if (pot.isEmpty) f else null
  }

}
