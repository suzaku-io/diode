package diode

/**
  * A read-only version of ModelR that doesn't know about the root model.
  *
  * @tparam S
  *   Type of the reader value
  */
trait ModelRO[S] {

  /**
    * Type of a new reader returned by functions such as `zoom`
    * @tparam T
    */
  type NewR[T] <: ModelRO[T]

  /**
    * Returns the value of the reader
    */
  def value: S

  /**
    * Returns the value of the reader
    */
  def apply(): S = value

  /**
    * Checks if `that` is equal to `this` using an appropriate equality check
    *
    * @param that
    *   Value to compare with
    * @return
    */
  def ===(that: S): Boolean

  def =!=(that: S): Boolean = ! ===(that)

  /**
    * Zooms into the model using the provided accessor function
    *
    * @param get
    *   Function to go from current reader to a new value
    */
  def zoom[T](get: S => T)(implicit feq: FastEq[_ >: T]): NewR[T]

  /**
    * Maps over current reader into a new value provided by `f`. Reader type `S` must be of type `F[A]`, for example
    * `Option[A]`.
    *
    * @param f
    *   The function to apply
    */
  def map[F[_], A, B](f: A => B)(implicit ev: S =:= F[A], monad: Monad[F], feq: FastEq[_ >: B]): NewR[F[B]] =
    zoomMap((_: S) => ev(value))(f)

  /**
    * FlatMaps over current reader into a new value provided by `f`. Reader type `S` must be of type `F[A]`, for example
    * `Option[A]`.
    *
    * @param f
    *   The function to apply, must return a value of type `F[B]`
    */
  def flatMap[F[_], A, B](f: A => F[B])(implicit ev: S =:= F[A], monad: Monad[F], feq: FastEq[_ >: B]): NewR[F[B]] =
    zoomFlatMap((_: S) => ev(value))(f)

  /**
    * Zooms into the model and maps over the zoomed value, which must be of type `F[A]`
    *
    * @param fa
    *   Zooming function
    * @param f
    *   The function to apply
    */
  def zoomMap[F[_], A, B](fa: S => F[A])(f: A => B)(implicit monad: Monad[F], feq: FastEq[_ >: B]): NewR[F[B]]

  /**
    * Zooms into the model and flatMaps over the zoomed value, which must be of type `F[A]`
    *
    * @param fa
    *   Zooming function
    * @param f
    *   The function to apply, must return a value of type `F[B]`
    */
  def zoomFlatMap[F[_], A, B](fa: S => F[A])(f: A => F[B])(implicit monad: Monad[F], feq: FastEq[_ >: B]): NewR[F[B]]
}

/**
  * Base trait for all model readers
  *
  * @tparam M
  *   Type of the base model
  * @tparam S
  *   Type of the reader value
  */
trait ModelR[M, S] extends ModelRO[S] {
  override type NewR[T] = ModelR[M, T]

  /**
    * Evaluates the reader against a supplied `model`
    */
  def eval(model: M): S

  /**
    * Returns the root model reader of this reader
    */
  def root: ModelR[M, M]

  /**
    * Combines this reader with another reader to provide a new reader returning a tuple of the values of the two original
    * readers.
    *
    * @param that
    *   The other reader
    */
  def zip[SS](that: ModelR[M, SS])(implicit feqS: FastEq[_ >: S], feqSS: FastEq[_ >: SS]): ModelR[M, (S, SS)]
}

/**
  * Base trait for all model writers
  *
  * @tparam M
  *   Type of the base model
  * @tparam S
  *   Type of the reader/writer value
  */
trait ModelRW[M, S] extends ModelR[M, S] with ZoomTo[M, S] {

  /**
    * Updates the model using the value provided and returns the updated model.
    */
  def updated(newValue: S): M

  /**
    * Updates the supplied model with the value provided and returns the updated model.
    */
  def updatedWith(model: M, newValue: S): M

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
    * Zooms into the model and maps over the zoomed value, which must be of type `F[A]`. The `set` function is used to update
    * the model with a new value.
    *
    * @param fa
    *   Zooming function
    * @param f
    *   The function to apply
    * @param set
    *   Function to update the model with a new value
    */
  def zoomMapRW[F[_], A, B](fa: S => F[A])(f: A => B)(
      set: (S, F[B]) => S
  )(implicit monad: Monad[F], feq: FastEq[_ >: B]): ModelRW[M, F[B]]

  /**
    * Zooms into the model and flatMaps over the zoomed value, which must be of type `F[A]`. The `set` function is used to
    * update the model with a new value.
    *
    * @param fa
    *   Zooming function
    * @param f
    *   The function to apply
    * @param set
    *   Function to update the model with a new value
    */
  def zoomFlatMapRW[F[_], A, B](fa: S => F[A])(f: A => F[B])(
      set: (S, F[B]) => S
  )(implicit monad: Monad[F], feq: FastEq[_ >: B]): ModelRW[M, F[B]]

}

/**
  * Implements common functionality for all model readers
  *
  * @tparam M
  *   Type of the base model
  * @tparam S
  *   Type of the reader value
  */
trait BaseModelR[M, S] extends ModelR[M, S] {
  override def eval(model: M): S

  override def value = eval(root.value)

  override def zoom[T](get: S => T)(implicit feq: FastEq[_ >: T]) =
    new ZoomModelR[M, T](root, get compose this.eval)

  override def zip[SS](that: ModelR[M, SS])(implicit feqS: FastEq[_ >: S], feqSS: FastEq[_ >: SS]) =
    new ZipModelR[M, S, SS](root, eval, that.eval)

  override def zoomMap[F[_], A, B](fa: S => F[A])(
      f: A => B
  )(implicit monad: Monad[F], feq: FastEq[_ >: B]): ModelR[M, F[B]] =
    new MapModelR(root, fa compose eval, f)

  override def zoomFlatMap[F[_], A, B](fa: S => F[A])(
      f: A => F[B]
  )(implicit monad: Monad[F], feq: FastEq[_ >: B]): ModelR[M, F[B]] =
    new FlatMapModelR(root, fa compose eval, f)
}

/**
  * Model reader for the root value
  */
class RootModelR[M <: AnyRef](get: => M) extends BaseModelR[M, M] {
  def root = this

  override def eval(model: M) = get

  override def value = get

  override def ===(that: M): Boolean = this eq that
}

/**
  * Model reader for a zoomed value
  */
class ZoomModelR[M, S](val root: ModelR[M, M], get: M => S)(implicit feq: FastEq[_ >: S]) extends BaseModelR[M, S] {
  override def eval(model: M) = get(model)

  override def ===(that: S): Boolean = feq.eqv(value, that)
}

trait MappedModelR[F[_], M, B] { self: ModelR[M, F[B]] =>
  protected def monad: Monad[F]
  protected def feq: FastEq[_ >: B]
  protected def mapValue: F[B]

  private var memoized = mapValue

  override def eval(model: M): F[B] = {
    val v = mapValue
    // update memoized value only when the value inside the monad changes
    if (!monad.isEqual(v, memoized)(feq.eqv)) {
      memoized = v
    }
    memoized
  }

  override def ===(that: F[B]) = monad.isEqual(value, that)(feq.eqv)
}

/**
  * Model reader for a mapped value
  */
class MapModelR[F[_], M, A, B](val root: ModelR[M, M], get: M => F[A], f: A => B)(implicit
    val monad: Monad[F],
    val feq: FastEq[_ >: B]
) extends BaseModelR[M, F[B]]
    with MappedModelR[F, M, B] {

  override protected def mapValue = monad.map(get(root.value))(f)
}

/**
  * Model reader for a flatMapped value
  */
class FlatMapModelR[F[_], M, A, B](val root: ModelR[M, M], get: M => F[A], f: A => F[B])(implicit
    val monad: Monad[F],
    val feq: FastEq[_ >: B]
) extends BaseModelR[M, F[B]]
    with MappedModelR[F, M, B] {

  override protected def mapValue = monad.flatMap(get(root.value))(f)
}

/**
  * Model reader for two zipped readers
  */
class ZipModelR[M, S, SS](val root: ModelR[M, M], get1: M => S, get2: M => SS)(implicit
    feqS: FastEq[_ >: S],
    feqSS: FastEq[_ >: SS]
) extends BaseModelR[M, (S, SS)] {
  // initial value for zipped
  private var zipped = (get1(root.value), get2(root.value))

  // ZipModel uses optimized `get` functions to check if the contents of the tuple has changed or not
  override def eval(model: M) = {
    // check if inner references have changed
    val v1 = get1(root.value)
    val v2 = get2(root.value)
    if (feqS.neqv(zipped._1, v1) || feqSS.neqv(zipped._2, v2)) {
      // create a new tuple
      zipped = (v1, v2)
    }
    zipped
  }

  override def ===(that: (S, SS)) = {
    // using fast eq is required to support zip in subscribe
    feqS.eqv(get1(root.value), that._1) && feqSS.eqv(get2(root.value), that._2)
  }
}

/**
  * Implements common functionality for all reader/writers
  *
  * @tparam M
  *   Type of the base model
  * @tparam S
  *   Type of the reader/writer value
  */
trait BaseModelRW[M, S] extends ModelRW[M, S] with BaseModelR[M, S] {
  override def zoomRW[U](get: S => U)(set: (S, U) => S)(implicit feq: FastEq[_ >: U]) =
    new ZoomModelRW[M, U](root, get compose eval, (s, u) => updatedWith(s, set(eval(s), u)))

  override def zoomMapRW[F[_], A, B](fa: S => F[A])(f: A => B)(
      set: (S, F[B]) => S
  )(implicit monad: Monad[F], feq: FastEq[_ >: B]) =
    new MapModelRW(root, fa compose eval, f)((s, u) => updatedWith(s, set(eval(s), u)))

  override def zoomFlatMapRW[F[_], A, B](fa: S => F[A])(f: A => F[B])(
      set: (S, F[B]) => S
  )(implicit monad: Monad[F], feq: FastEq[_ >: B]) =
    new FlatMapModelRW(root, fa compose eval, f)((s, u) => updatedWith(s, set(eval(s), u)))

  override def updated(newValue: S) = updatedWith(root.value, newValue)
}

/**
  * Model reader/writer for the root value
  */
class RootModelRW[M <: AnyRef](get: => M) extends RootModelR(get) with BaseModelRW[M, M] {
  override def updatedWith(model: M, value: M) = value

  // override for root because it's a simpler case
  override def zoomRW[T](get: M => T)(set: (M, T) => M)(implicit feq: FastEq[_ >: T]) =
    new ZoomModelRW[M, T](this, get, set)
}

/**
  * Model reader/writer for a zoomed value
  */
class ZoomModelRW[M, S](root: ModelR[M, M], get: M => S, set: (M, S) => M)(implicit feq: FastEq[_ >: S])
    extends ZoomModelR(root, get)
    with BaseModelRW[M, S] {
  override def updatedWith(model: M, value: S) = set(model, value)
}

/**
  * Model reader/writer for a mapped value
  */
class MapModelRW[F[_], M, A, B](root: ModelR[M, M], get: M => F[A], f: A => B)(set: (M, F[B]) => M)(implicit
    monad: Monad[F],
    feq: FastEq[_ >: B]
) extends MapModelR(root, get, f)
    with BaseModelRW[M, F[B]] {
  override def updatedWith(model: M, value: F[B]) = set(model, value)
}

/**
  * Model reader/writer for a flatMapped value
  */
class FlatMapModelRW[F[_], M, A, B](root: ModelR[M, M], get: M => F[A], f: A => F[B])(set: (M, F[B]) => M)(implicit
    monad: Monad[F],
    feq: FastEq[_ >: B]
) extends FlatMapModelR(root, get, f)
    with BaseModelRW[M, F[B]] {
  override def updatedWith(model: M, value: F[B]) = set(model, value)
}
