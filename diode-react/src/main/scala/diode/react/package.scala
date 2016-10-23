package diode

import japgolly.scalajs.react.CtorType.Props
import japgolly.scalajs.react.ScalaComponent._
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.ReactElement

package object react {
  //type ReactConnectProxy[S] = Props[(ModelProxy[S]) => ReactElement, Unmounted[(ModelProxy[S]) => ReactElement, _, _]]
  //type ReactConnectProxy[S] = Props[ModelProxy[S] => ReactElement,ScalaComponent.Unmounted[ModelProxy[S] => ReactElement, _, _]]

  type ReactConnectProxy[S] = (ModelProxy[S] => ReactElement) => Component.Unmounted[ModelProxy[S] => ReactElement,_]

}
