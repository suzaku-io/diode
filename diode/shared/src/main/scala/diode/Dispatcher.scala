package diode

trait Dispatcher {
  def dispatch(action: AnyRef): Unit

  def apply(action: AnyRef) = dispatch(action)
}
