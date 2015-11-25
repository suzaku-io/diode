package diode.react

import diode._
import japgolly.scalajs.react._
import scala.language.existentials

/**
  * Wraps a model reader, dispatcher and React connector to be passed to React components
  * in props.
  */
case class ComponentModel[S](modelReader: ModelR[S], dispatch: AnyRef => Callback, connector: ReactConnector[_ <: AnyRef]) {
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

  /**
    * Connects a React component into the Circuit by wrapping it in another component that listens to
    * relevant state changes and updates the wrapped component as needed.
    *
    * @param zoomFunc Function to retrieve relevant piece from the model
    * @param compB Function that creates the wrapped component
    * @return A React component
    */
  def connect[S <: AnyRef, C](zoomFunc: M => S)(compB: ComponentModel[S] => C)
                             (implicit ev: C => ReactElement): ReactComponentU[Unit, S, _, TopNode] = {
    connect(circuit.zoom(zoomFunc))(compB)
  }

  /**
    * Connects a React component into the Circuit by wrapping it in another component that listens to
    * relevant state changes and updates the wrapped component as needed.
    *
    * @param modelReader A reader that returns the piece of model we are interested in
    * @param compB Function that creates the wrapped component
    * @return A React component
    */
  def connect[S <: AnyRef, C](modelReader: ModelR[S])(compB: ComponentModel[S] => C)
                             (implicit ev: C => ReactElement): ReactComponentU[Unit, S, _, TopNode] = {

    class Backend(t: BackendScope[Unit, S]) {
      private var unsubscribe = Option.empty[() => Unit]

      def didMount = Callback {
        // subscribe to model changes
        // we can provide a cursor that ignores the model parameter, because we know the model already :)
        unsubscribe = Some(circuit.subscribe(changeHandler, _ => modelReader.value))
      }

      def willUnmount = Callback {
        unsubscribe.foreach(f => f())
        unsubscribe = None
      }

      private def changeHandler(): Unit = {
        // modify state if we are mounted and state has actually changed
        if(t.isMounted() && (t.accessDirect.state ne modelReader.value))
          t.accessDirect.setState(modelReader.value)
      }

      def render(s: S) = wrap(modelReader)(compB)
    }

    ReactComponentB[Unit]("DiodeWrapper")
      .initialState(modelReader.value)
      .renderBackend[Backend]
      .componentDidMount(scope => scope.backend.didMount)
      .componentWillUnmount(scope => scope.backend.willUnmount)
      .shouldComponentUpdate(scope => scope.currentState ne scope.nextState)
      .buildU.apply()
  }

  /**
    * Wraps a React component by providing it an instace of ComponentModel for easy access to the model and dispatcher.
    *
    * @param zoomFunc Function to retrieve relevant piece from the model
    * @param compB Function that creates the wrapped component
    * @return The component returned by `compB`
    */
  def wrap[S <: AnyRef, C](zoomFunc: M => S)(compB: ComponentModel[S] => C)(implicit ev: C => ReactElement): C = {
    wrap(circuit.zoom(zoomFunc))(compB)
  }

  /**
    * Wraps a React component by providing it an instace of ComponentModel for easy access to the model and dispatcher.
    *
    * @param modelReader A reader that returns the piece of model we are interested in
    * @param compB Function that creates the wrapped component
    * @return The component returned by `compB`
    */
  def wrap[S <: AnyRef, C](modelReader: ModelR[S])(compB: ComponentModel[S] => C)(implicit ev: C => ReactElement): C = {
    compB(ComponentModel(modelReader, action => Callback(circuit.dispatch(action)), ReactConnector.this))
  }
}
