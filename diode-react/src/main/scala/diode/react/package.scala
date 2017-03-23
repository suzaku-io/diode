package diode

import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Generic.UnmountedWithRoot
import japgolly.scalajs.react.vdom.VdomElement

package object react {
  type ReactConnectProps[A] = ModelProxy[A] => VdomElement
  type ReactConnectProxy[A] = CtorType.Props[ReactConnectProps[A], UnmountedWithRoot[ReactConnectProps[A], _, _, _]]
}
