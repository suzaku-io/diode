
package object diode {

  /**
    * Provides a type class instance of ActionType for AnyRef, allowing you to dispatch anything as an action.
    *
    * Bring to scope with `import diode.AnyRefAction._`
    */
  object AnyRefAction {

    implicit object aType extends ActionType[AnyRef]

  }
}
