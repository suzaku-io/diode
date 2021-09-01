package diode

import macros.GenLens

trait ZoomTo[M, S] {

  /**
    * Zooms into the model using the provided `get` function. The `set` function is used to update the model with a new
    * value.
    *
    * @param get
    *   Function to go from current reader to a new value
    * @param set
    *   Function to update the model with a new value
    */
  def zoomRW[T](get: S => T)(set: (S, T) => S)(implicit feq: FastEq[_ >: T]): ModelRW[M, T]

  /**
    * An easier way to zoom into a RW model by just specifying a single chained accessor for the field. This works for cases
    * like `zoomTo(_.a.b.c)` but not for more complex cases such as `zoomTo(_.a.b(0))`. Uses a macro to generate appropriate
    * update function.
    *
    * @param field
    *   Field to access in the model
    */
  inline def zoomTo[T](inline field: S => T)(implicit feq: FastEq[? >: T]): ModelRW[M, T] = ${
    GenLens.generate[M, S, T]('{ field }, '{ this }, '{ feq })
  }

}
