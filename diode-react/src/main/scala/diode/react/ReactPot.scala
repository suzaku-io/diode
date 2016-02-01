package diode.react

import diode.data.{PendingBase, Pot}
import japgolly.scalajs.react.ReactNode

object ReactPot {

  import scala.language.implicitConversions

  implicit def potRenderer[A](pot: Pot[A]) : potWithReact[A] = potWithReact(pot, Nil)

  implicit def toReact(p : potWithReact[_]) : ReactNode = p.nodes


  case class potWithReact[A](pot: Pot[A], nodes: List[ReactNode]) {
    /**
      * Render non-empty (ready or stale) content
 *
      * @param f Transforms Pot value into a ReactNode
      * @return
      */
    def render(f: A => ReactNode): potWithReact[A] =
      if (pot.nonEmpty) copy(nodes = f(pot.get) :: nodes) else this

    /**
      * Render content in Ready state, not including stale states
 *
      * @param f Transforms Pot value into a ReactNode
      * @return
      */
    def renderReady(f: A => ReactNode): potWithReact[A] =
      if (pot.isReady) copy(nodes = f(pot.get) :: nodes) else this

    /**
      * Render when Pot is pending
 *
      * @param f Transforms duration time into a ReactNode
      * @return
      */
    def renderPending(f: Int => ReactNode): potWithReact[A] =
      if (pot.isPending) copy(nodes = f(pot.asInstanceOf[PendingBase].duration()) :: nodes) else this

    /**
      * Render when Pot is pending with a filter on duration
 *
      * @param b Filter based on duration value
      * @param f Transforms duration time into a ReactNode
      * @return
      */
    def renderPending(b: Int => Boolean, f: Int => ReactNode): potWithReact[A] = {
      if (pot.isPending) {
        val duration = pot.asInstanceOf[PendingBase].duration()
        if (b(duration)) copy(nodes = f(duration) :: nodes) else this
      } else null
    }

    /**
      * Render when Pot has failed
 *
      * @param f Transforms an exception into a ReactNode
      * @return
      */
    def renderFailed(f: Throwable => ReactNode): potWithReact[A] =
      if (pot.isFailed) copy(nodes = f(pot.exceptionOption.get) :: nodes) else this

    /**
      * Render stale content (`PendingStale` or `FailedStale`)
 *
      * @param f Transforms Pot value into a ReactNode
      * @return
      */
    def renderStale(f: A => ReactNode): potWithReact[A] =
      if (pot.isStale) copy(nodes = f(pot.get) :: nodes) else this

    /**
      * Render when Pot is empty
 *
      * @param f Returns a ReactNode
      * @return
      */
    def renderEmpty(f: => ReactNode): potWithReact[A] =
      if (pot.isEmpty) copy(nodes = f :: nodes) else this
  }

}
