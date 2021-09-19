package diode

import diode.macros.GenLens

trait ZoomTo[M, S] {

  /**
    * An easier way to zoom into a RW model by just specifying a single chained accessor for the field. This works for cases
    * like `zoomTo(_.a.b.c)` but not for more complex cases such as `zoomTo(_.a.b(0))`. Uses a macro to generate appropriate
    * update function.
    *
    * @param field
    *   Field to access in the model
    */
  def zoomTo[T](field: S => T): ModelRW[M, T] = macro GenLens.generate[M, S, T]
}
