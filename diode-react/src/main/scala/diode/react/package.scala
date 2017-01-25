package diode

import japgolly.scalajs.react._

package object react {
  type ReactConnectProxy[A] = ReactComponentC.ReqProps[ModelProxy[A] => ReactElement, A, _, TopNode]
}
