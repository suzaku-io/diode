package diode.react

import diode._
import japgolly.scalajs.react._
import scala.language.existentials

case class ComponentModel[S] private[diode] (modelReader: ModelR[S], dispatch: AnyRef => Callback, connector: ReactConnector[_ <: AnyRef]) {
  def value = modelReader.value

  def apply() = modelReader.value

  def zoom[T](f: S => T) = ComponentModel(modelReader.zoom(f), dispatch, connector)

  def connect[T <: AnyRef, C](f: S => T)(compB: ComponentModel[T] => C)
                             (implicit ev: C => ReactElement): ReactComponentU[Unit, T, _, TopNode] = {
    connector.connect(modelReader.zoom(f))(compB)
  }
}

trait ReactConnector[M <: AnyRef] {
  circuit: Circuit[M] =>

  @inline
  def connect[S <: AnyRef, C](zoomFunc: M => S)(compB: ComponentModel[S] => C)
                             (implicit ev: C => ReactElement): ReactComponentU[Unit, S, _, TopNode] = {
    connect(circuit.zoom(zoomFunc))(compB)
  }

  def connect[S <: AnyRef, C](modelReader: ModelR[S])(compB: ComponentModel[S] => C)
                             (implicit ev: C => ReactElement): ReactComponentU[Unit, S, _, TopNode] = {
    class Backend(t: BackendScope[Unit, S]) {
      private var unsubscribe = Option.empty[() => Unit]

      def didMount = Callback {
        unsubscribe = Some(circuit.subscribe(changeHandler))
      }

      def willUnmount = Callback {
        unsubscribe.foreach(f => f())
        unsubscribe = None
      }

      private def changeHandler(): Unit = {
        if(t.accessDirect.state ne modelReader.value)
          t.accessDirect.setState(modelReader.value)
      }

      def render(s: S) = compB(ComponentModel(modelReader, action => Callback(circuit.dispatch(action)), ReactConnector.this))
    }

    ReactComponentB[Unit]("DiodeWrapper")
      .initialState(modelReader.value)
      .renderBackend[Backend]
      .componentDidMount(scope => scope.backend.didMount)
      .componentWillUnmount(scope => scope.backend.willUnmount)
      .shouldComponentUpdate(scope => scope.currentState ne scope.nextState)
      .buildU.apply()
  }
}
