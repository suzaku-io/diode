package diode

import scala.language.higherKinds
import scala.reflect.ClassTag

/**
  * Base trait for all model readers
  *
  * @tparam M Type of the base model
  * @tparam S Type of the reader value
  */
trait ModelR[M, S] {
  /**
    * Returns the value of the reader
    */
  def value: S

  /**
    * Returns the value of the reader
    */
  def apply(): S = value

  /**
    * Evaluates the reader against a supplied `model`
    */
  def eval(model: M): S

  /**
    * "Zooms" into the model using the provided accessor function
    * @param get Function to go from current reader to a new value
    */
  def zoom[T](get: S => T): ModelR[M, T]

  /**
    * Maps over current reader into a new value provided by `f`. Reader type `S` must be of type `F[A]`,
    * for example `Option[A]`.
    * @param f The function to apply
    */
  def map[F[_], A, B](f: A => B)(implicit ev: S =:= F[A], functor: Functor[F], ct: ClassTag[B]): ModelR[M, F[B]] =
    zoomMap((_: S) => ev(value))(f)

  /**
    * FlatMaps over current reader into a new value provided by `f`. Reader type `S` must be of type `F[A]`,
    * for example `Option[A]`.
    * @param f The function to apply, must return a value of type `F[B]`
    */
  def flatMap[F[_], A, B](f: A => F[B])(implicit ev: S =:= F[A], functor: Functor[F], ct: ClassTag[B]): ModelR[M, F[B]] =
    zoomFlatMap((_: S) => ev(value))(f)

  /**
    * Zooms into the model and maps over the zoomed value, which must be of type `F[A]`
    * @param fa Zooming function
    * @param f The function to apply
    */
  def zoomMap[F[_], A, B](fa: S => F[A])(f: A => B)(implicit functor: Functor[F], ct: ClassTag[B]): ModelR[M, F[B]]

  /**
    * Zooms into the model and flatMaps over the zoomed value, which must be of type `F[A]`
    * @param fa Zooming function
    * @param f The function to apply, must return a value of type `F[B]`
    */
  def zoomFlatMap[F[_], A, B](fa: S => F[A])(f: A => F[B])(implicit functor: Functor[F], ct: ClassTag[B]): ModelR[M, F[B]]

  /**
    * Combines this reader with another reader to provide a new reader returning a tuple of the values
    * of the two original readers.
    * @param that The other reader
    */
  def zip[SS](that: ModelR[M, SS])(implicit cts: ClassTag[S], ctss: ClassTag[SS]): ModelR[M, (S, SS)]
}

/**
  * Base trait for all model writers
  *
  * @tparam M Type of the base model
  * @tparam S Type of the reader/writer value
  */
trait ModelRW[M, S] extends ModelR[M, S] {
  /**
    * Updatews the model using the value provided and returns the updated model.
    */
  def updated(newValue: S): M

  /**
    * Zooms into the model using the provided `get` function. The `set` function is used to
    * update the model with a new value.
    * @param get Function to go from current reader to a new value
    * @param set Function to update the model with a new value
    */
  def zoomRW[T](get: S => T)(set: (S, T) => S): ModelRW[M, T]

  /**
    * Zooms into the model and maps over the zoomed value, which must be of type `F[A]`. The `set` function is used to
    * update the model with a new value.
    * @param fa Zooming function
    * @param f The function to apply
    * @param set Function to update the model with a new value
    */
  def zoomMapRW[F[_], A, B](fa: S => F[A])(f: A => B)(set: (S, F[B]) => S)(implicit functor: Functor[F], ct: ClassTag[B]): ModelRW[M, F[B]]

  /**
    * Zooms into the model and flatMaps over the zoomed value, which must be of type `F[A]`. The `set` function is used to
    * update the model with a new value.
    * @param fa Zooming function
    * @param f The function to apply
    * @param set Function to update the model with a new value
    */
  def zoomFlatMapRW[F[_], A, B](fa: S => F[A])(f: A => F[B])(set: (S, F[B]) => S)(implicit functor: Functor[F], ct: ClassTag[B]): ModelRW[M, F[B]]
}

/**
  * Helper trait providing equality checking for both values and references
  */
trait ValueRefEq {
  def valueEq[A](v1: A, v2: A) = v1 == v2

  def refEq[A](v1: A, v2: A) = v1.asInstanceOf[AnyRef] eq v2.asInstanceOf[AnyRef]

  /**
    * Choose an equality function based on the type (AnyVal vs. AnyRef)
    *
    * @param ct ClassTag for the type
    */
  def chooseEq[A](ct: ClassTag[A]) = {
    ct match {
      case ClassTag.Char => valueEq[A] _
      case ClassTag.Int => valueEq[A] _
      case ClassTag.Boolean => valueEq[A] _
      case ClassTag.Long => valueEq[A] _
      case ClassTag.Byte => valueEq[A] _
      case ClassTag.Short => valueEq[A] _
      case ClassTag.Float => valueEq[A] _
      case ClassTag.Double => valueEq[A] _
      case _ => refEq[A] _
    }
  }
}

/**
  * Implements common functionality for all model readers
  *
  * @tparam M Type of the base model
  * @tparam S Type of the reader value
  */
trait BaseModelR[M, S] extends ModelR[M, S] {
  protected def root: ModelR[M, M]

  protected def getF(model: M): S

  override def value = getF(root.value)

  override def eval(model: M) = getF(model)

  override def zoom[T](get: S => T) =
    new ZoomModelR[M, T](root, get compose this.getF)

  override def zip[SS](that: ModelR[M, SS])(implicit cts: ClassTag[S], ctss: ClassTag[SS]) =
    new ZipModelR[M, S, SS](root, eval, that.eval)

  override def zoomMap[F[_], A, B](fa: S => F[A])(f: A => B)(implicit functor: Functor[F], ct: ClassTag[B]): ModelR[M, F[B]] =
    new MapModelR(root, fa compose getF, f)

  override def zoomFlatMap[F[_], A, B](fa: S => F[A])(f: A => F[B])(implicit functor: Functor[F], ct: ClassTag[B]): ModelR[M, F[B]] =
    new FlatMapModelR(root, fa compose getF, f)
}

/**
  * Model reader for the root value
  */
class RootModelR[M](get: => M) extends BaseModelR[M, M] {
  protected def root = this

  protected def getF(model: M) = get

  override def value = get

  override def eval(model: M) = get
}

/**
  * Model reader for a zoomed value
  */
class ZoomModelR[M, S](protected val root: ModelR[M, M], get: M => S) extends BaseModelR[M, S] {
  protected def getF(model: M) = get(model)
}

trait MappedModelR[F[_], M, B] extends ValueRefEq {
  def functor: Functor[F]
  def ct: ClassTag[B]
  def mapValue: F[B]

  private var memoized = mapValue
  private val eqF = chooseEq(ct)

  protected def getF(model: M): F[B] = {
    val v = mapValue
    // update memoized value only when the value inside the functor changes
    if (!functor.isEqual(v, memoized)(eqF)) {
      memoized = v
    }
    memoized
  }
}

/**
  * Model reader for a mapped value
  */
class MapModelR[F[_], M, A, B](protected val root: ModelR[M, M], get: M => F[A], f: A => B)(implicit val functor: Functor[F], val ct: ClassTag[B])
  extends BaseModelR[M, F[B]] with MappedModelR[F, M, B] {

  def mapValue = functor.map(get(root.value))(f)
}

/**
  * Model reader for a flatMapped value
  */
class FlatMapModelR[F[_], M, A, B](protected val root: ModelR[M, M], get: M => F[A], f: A => F[B])(implicit val functor: Functor[F], val ct: ClassTag[B])
  extends BaseModelR[M, F[B]] with MappedModelR[F, M, B] {

  def mapValue = functor.flatMap(get(root.value))(f)
}

/**
  * Model reader for two zipped readers
  */
class ZipModelR[M, S, SS](protected val root: ModelR[M, M], get1: M => S, get2: M => SS)(implicit cts: ClassTag[S], ctss: ClassTag[SS])
  extends BaseModelR[M, (S, SS)] with ValueRefEq {
  // initial value for zipped
  private var zipped = (get1(root.value), get2(root.value))
  // choose equality function for each type in the constructor
  private val eqS = chooseEq(cts)
  private val eqSS = chooseEq(ctss)

  // ZipModel uses optimized `get` functions to check if the contents of the tuple has changed or not
  // Different comparison functions are used for boxed values and real references
  protected def getF(model: M) = {
    // check if inner references have changed
    val v1 = get1(root.value)
    val v2 = get2(root.value)
    if (!eqS(zipped._1, v1) || !eqSS(zipped._2, v2)) {
      // create a new tuple
      zipped = (v1, v2)
    }
    zipped
  }
}

/**
  * Implements common functionality for all reader/writers
  *
  * @tparam M Type of the base model
  * @tparam S Type of the reader/writer value
  */
trait BaseModelRW[M, S] extends ModelRW[M, S] with BaseModelR[M, S] {
  protected def setF(model: M, value: S): M

  override def zoomRW[U](get: S => U)(set: (S, U) => S) =
    new ZoomModelRW[M, U](root, get compose getF, (s, u) => setF(s, set(getF(s), u)))

  override def zoomMapRW[F[_], A, B](fa: S => F[A])(f: A => B)(set: (S, F[B]) => S)(implicit functor: Functor[F], ct: ClassTag[B]) =
    new MapModelRW(root, fa compose getF, f)((s, u) => setF(s, set(getF(s), u)))

  override def zoomFlatMapRW[F[_], A, B](fa: S => F[A])(f: A => F[B])(set: (S, F[B]) => S)(implicit functor: Functor[F], ct: ClassTag[B]) =
    new FlatMapModelRW(root, fa compose getF, f)((s, u) => setF(s, set(getF(s), u)))

  override def updated(newValue: S) = setF(root.value, newValue)
}

/**
  * Model reader/writer for the root value
  */
class RootModelRW[M](get: => M) extends RootModelR(get) with BaseModelRW[M, M] {
  protected override def setF(model: M, value: M) = value

  override def zoomRW[T](get: M => T)(set: (M, T) => M) =
    new ZoomModelRW[M, T](this, get, (s, u) => set(value, u))
}

/**
  * Model reader/writer for a zoomed value
  */
class ZoomModelRW[M, S](root: ModelR[M, M], get: M => S, set: (M, S) => M) extends ZoomModelR(root, get) with BaseModelRW[M, S] {
  protected override def setF(model: M, value: S) = set(model, value)
}

/**
  * Model reader/writer for a mapped value
  */
class MapModelRW[F[_], M, A, B](root: ModelR[M, M], get: M => F[A], f: A => B)(set: (M, F[B]) => M)(implicit functor: Functor[F], ct: ClassTag[B])
  extends MapModelR(root, get, f) with BaseModelRW[M, F[B]] {
  protected override def setF(model: M, value: F[B]) = set(model, value)
}

/**
  * Model reader/writer for a flatMapped value
  */
class FlatMapModelRW[F[_], M, A, B](root: ModelR[M, M], get: M => F[A], f: A => F[B])(set: (M, F[B]) => M)(implicit functor: Functor[F], ct: ClassTag[B])
  extends FlatMapModelR(root, get, f) with BaseModelRW[M, F[B]] {
  protected override def setF(model: M, value: F[B]) = set(model, value)
}
