package diode

trait ModelR[+S] {
  def value: S

  def zoom[T](get: S => T): ModelR[T]
}

trait ModelRW[M, S] extends ModelR[S] {
  def update(newValue: S): M

  def zoomRW[T](get: S => T)(set: (S, T) => S): ModelRW[M, T]
}

class RootModelR[M](get: => M) extends ModelR[M] {
  override def value = get

  override def zoom[T](get: M => T) = new ZoomModelR[M, T](this, get)
}

class ZoomModelR[M, +T](root: ModelR[M], get: M => T) extends ModelR[T] {
  override def value = get(root.value)

  override def zoom[U](get: T => U) = new ZoomModelR[M, U](root, get compose this.get)
}

class RootModelRW[M](get: => M) extends RootModelR(get) with ModelRW[M, M] {
  override def zoomRW[T](get: M => T)(set: (M, T) => M) =
    new ZoomModelRW[M, T](this, get, (s, u) => set(value, u))

  override def update(newValue: M) = newValue
}

class ZoomModelRW[M, T](root: RootModelR[M], get: M => T, set: (M, T) => M) extends ZoomModelR(root, get) with ModelRW[M, T] {
  override def zoomRW[U](get: T => U)(set: (T, U) => T) =
    new ZoomModelRW[M, U](root, get compose this.get, (s, u) => this.set(s, set(this.get(s), u)))

  override def update(newValue: T) = set(root.value, newValue)
}
