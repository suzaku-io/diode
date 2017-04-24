package diode

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.VdomElement

package object react {

  type ReactConnectProxy[S] = CtorType.Props[(ModelProxy[S]) => VdomElement, ScalaComponent.Unmounted[(ModelProxy[S]) => VdomElement, _, _]]
}
