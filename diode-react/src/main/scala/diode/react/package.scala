package diode

import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Generic.Unmounted
import japgolly.scalajs.react.vdom.VdomElement

package object react {
  type ReactConnectProxy[A] = CtorType.Props[ModelProxy[A] => VdomElement, Unmounted[ModelProxy[A] => VdomElement, _]]
}
