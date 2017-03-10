package diode

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.VdomElement

package object react {
  //type ReactConnectProxy[S] = Props[(ModelProxy[S]) => ReactElement, Unmounted[(ModelProxy[S]) => ReactElement, _, _]]
  //type ReactConnectProxy[S] = Props[ModelProxy[S] => ReactElement,ScalaComponent.Unmounted[ModelProxy[S] => ReactElement, _, _]]

  //type ReactConnectProxy[S] = (ModelProxy[S] => VdomElement) => GenericComponent.Unmounted[ModelProxy[S] => VdomElement,_]
  type ReactConnectProxy[S] = CtorType.Props[(ModelProxy[S]) => VdomElement, ScalaComponent.Unmounted[(ModelProxy[S]) => VdomElement, _, _]]
}
