package diode.react

import diode._
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.VdomElement

import scala.language.existentials
import scala.scalajs.js

/**
  * Wraps a model reader, dispatcher and React connector to be passed to React components
  * in props.
  */
case class ModelProxy[S](modelReader: ModelRO[S], theDispatch: Any => Unit, connector: ReactConnector[_ <: AnyRef]) {
  def value = modelReader()

  /**
    * Perform a dispatch action in a `Callback`. Use `dispatchCB` instead to make meaning explicit.
    */
  @deprecated("Use dispatchCB instead", "1.1.0")
  def dispatch[A: ActionType](action: A): Callback = dispatchCB(action)

  /**
    * Perform a dispatch action in a `Callback`
    */
  def dispatchCB[A: ActionType](action: A): Callback = Callback(theDispatch(action))

  /**
    * Dispatch an action right now
    */
  def dispatchNow[A: ActionType](action: A): Unit = theDispatch(action)

  def apply() = modelReader()

  def zoom[T](f: S => T)(implicit feq: FastEq[_ >: T]) = ModelProxy(modelReader.zoom(f), theDispatch, connector)

  def wrap[T <: AnyRef, C](f: S => T)(compB: ModelProxy[T] => C)(implicit ev: C => VdomElement, feq: FastEq[_ >: T]): C = compB(zoom(f))

  def connect[T <: AnyRef](f: S => T)(implicit feq: FastEq[_ >: T]): ReactConnectProxy[T] = {
    connector.connect(modelReader.zoom(f))
  }
}

trait ReactConnector[M <: AnyRef] { circuit: Circuit[M] =>

  /**
    * Wraps a React component by providing it an instance of ModelProxy for easy access to the model and dispatcher.
    *
    * @param zoomFunc Function to retrieve relevant piece from the model
    * @param compB    Function that creates the wrapped component
    * @return The component returned by `compB`
    */
  def wrap[S <: AnyRef, C](zoomFunc: M => S)(compB: ModelProxy[S] => C)(implicit ev: C => VdomElement, feq: FastEq[_ >: S]): C = {
    wrap(circuit.zoom(zoomFunc))(compB)
  }

  /**
    * Wraps a React component by providing it an instance of ModelProxy for easy access to the model and dispatcher.
    *
    * @param modelReader A reader that returns the piece of model we are interested in
    * @param compB       Function that creates the wrapped component
    * @return The component returned by `compB`
    */
  def wrap[S <: AnyRef, C](modelReader: ModelRO[S])(compB: ModelProxy[S] => C)(implicit ev: C => VdomElement, feq: FastEq[_ >: S]): C = {
    implicit object aType extends ActionType[Any]
    compB(ModelProxy(modelReader, action => circuit.dispatch(action), ReactConnector.this))
  }

  /**
    * Connects a React component into the Circuit by wrapping it in another component that listens to
    * relevant state changes and updates the wrapped component as needed.
    *
    * @param zoomFunc Function to retrieve relevant piece from the model
    * @param key      Specifies a unique React key for this component.
    * @return A ReactConnectProxy
    */
  def connect[S <: AnyRef](zoomFunc: M => S, key: js.Any)(implicit feq: FastEq[_ >: S]): ReactConnectProxy[S] = {
    connect(circuit.zoom(zoomFunc), key)
  }

  /**
    * Connects a React component into the Circuit by wrapping it in another component that listens to
    * relevant state changes and updates the wrapped component as needed.
    *
    * @param zoomFunc Function to retrieve relevant piece from the model
    * @return A ReactConnectProxy
    */
  def connect[S <: AnyRef](zoomFunc: M => S)(implicit feq: FastEq[_ >: S]): ReactConnectProxy[S] = {
    connect(circuit.zoom(zoomFunc))
  }

  /**
    * Connects a React component into the Circuit by wrapping it in another component that listens to
    * relevant state changes and updates the wrapped component as needed.
    *
    * @param modelReader A reader that returns the piece of model we are interested in
    * @param key         Optional parameter specifying a unique React key for this component.
    * @return A ReactConnectProxy
    */
  def connect[S <: AnyRef](modelReader: ModelRO[S], key: js.UndefOr[js.Any] = js.undefined)(
      implicit feq: FastEq[_ >: S]): ReactConnectProxy[S] = {

    class Backend(t: BackendScope[ReactConnectProps[S], S]) {
      private var unsubscribe = Option.empty[() => Unit]

      def willMount = {
        // subscribe to model changes
        Callback {
          unsubscribe = Some(circuit.subscribe(modelReader.asInstanceOf[ModelR[M, S]])(changeHandler))
        } >> t.setState(modelReader())
      }

      def willUnmount = Callback {
        unsubscribe.foreach(f => f())
        unsubscribe = None
      }

      private def changeHandler(cursor: ModelRO[S]): Unit = {
        val isMounted = t.isMounted.map(_.getOrElse(true))
        val stateHasChanged = t.state.map(state => modelReader =!= state)

        def updateState(shouldUpdate: Boolean): Callback =
          Callback.when(shouldUpdate)(t.setState(cursor()))

        ((isMounted && stateHasChanged) >>= updateState).runNow()
      }

      def render(s: S, compB: ReactConnectProps[S]) = wrap(modelReader)(compB)
    }

    ScalaComponent.builder[ReactConnectProps[S]]("DiodeWrapper")
      .initialState(modelReader())
      .renderBackend[Backend]
      .componentWillMount(_.backend.willMount)
      .componentWillUnmount(_.backend.willUnmount)
      .shouldComponentUpdatePure(scope => (scope.currentState ne scope.nextState) || (scope.currentProps ne scope.nextProps))
      .build
      .withRawProp("key", key)
  }
}
