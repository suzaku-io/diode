package object diode {

  /**
    * Provides a type class instance of ActionType for Any, allowing you to dispatch anything as an action.
    *
    * Bring to scope with `import diode.AnyAction._`
    */
  object AnyAction {

    implicit object aType extends ActionType[Any]

  }

  type Subscriber[A] = (ModelRO[A] => Unit) => () => Unit
}
