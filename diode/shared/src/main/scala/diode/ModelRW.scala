package diode

import scala.reflect.ClassTag

trait ModelR[M, S] {
  def value: S

  def apply(): S = value

  def eval(model: M): S

  def zoom[T](get: S => T): ModelR[M, T]

  def zip[SS](that: ModelR[M, SS])(implicit cts: ClassTag[S], ctss: ClassTag[SS]): ModelR[M, (S, SS)]
}

trait ModelRW[M, S] extends ModelR[M, S] {
  def updated(newValue: S): M

  def zoomRW[T](get: S => T)(set: (S, T) => S): ModelRW[M, T]
}

class RootModelR[M](get: => M) extends ModelR[M, M] {
  override def value = get

  override def eval(model: M) = get

  override def zoom[T](get: M => T) = new ZoomModelR[M, T](this, get)

  override def zip[SS](that: ModelR[M, SS])(implicit cts: ClassTag[M], ctss: ClassTag[SS]) = new ZipModelR[M, M, SS](this, eval, that.eval)
}

class ZoomModelR[M, S](root: ModelR[M, M], get: M => S) extends ModelR[M, S] {
  override def value = get(root.value)

  override def eval(model: M) = get(model)

  override def zoom[T](get: S => T) = new ZoomModelR[M, T](root, get compose this.get)

  override def zip[SS](that: ModelR[M, SS])(implicit cts: ClassTag[S], ctss: ClassTag[SS]) = new ZipModelR[M, S, SS](root, eval, that.eval)
}

class ZipModelR[M, S, SS](root: ModelR[M, M], get1: M => S, get2: M => SS)(implicit cts: ClassTag[S], ctss: ClassTag[SS]) extends ModelR[M, (S, SS)] {
  private var zipped = Tuple2[S, SS](null.asInstanceOf[S], null.asInstanceOf[SS])

  // ZipModel uses optimized `get` functions to check if the contents of the tuple has changed or not
  // Different comparison functions are used for boxed values and real references
  private def getXX(eqS: (S, S) => Boolean, eqSS: (SS, SS) => Boolean)(model: M) = {
    // check if inner references have changed
    val v1 = get1(root.value)
    val v2 = get2(root.value)
    if (!eqS(zipped._1, v1) || !eqSS(zipped._2, v2)) {
      // create a new tuple
      zipped = (v1, v2)
    }
    zipped
  }

  def valueEq[A](v1: A, v2: A) = v1 == v2

  def refEq[A](v1: A, v2: A) = v1.asInstanceOf[AnyRef] eq v2.asInstanceOf[AnyRef]

  def chooseEq(ct: ClassTag[_]) = {
    ct match {
      case ClassTag.Char => valueEq _
      case ClassTag.Int => valueEq _
      case ClassTag.Boolean => valueEq _
      case ClassTag.Long => valueEq _
      case ClassTag.Byte => valueEq _
      case ClassTag.Short => valueEq _
      case ClassTag.Float => valueEq _
      case ClassTag.Double => valueEq _
      case _ => refEq _
    }
  }

  // choose correct getXX function base on types of S and SS
  private val get = getXX(chooseEq(cts), chooseEq(ctss)) _

  override def eval(model: M) = get(model)

  override def value = get(root.value)

  override def zoom[U](get: ((S, SS)) => U) = new ZoomModelR[M, U](root, get compose this.get)

  override def zip[SSS](that: ModelR[M, SSS])(implicit cts: ClassTag[(S, SS)], ctss: ClassTag[SSS]) = new ZipModelR[M, (S, SS), SSS](root, eval, that.eval)
}

class RootModelRW[M](get: => M) extends RootModelR(get) with ModelRW[M, M] {
  override def zoomRW[T](get: M => T)(set: (M, T) => M) =
    new ZoomModelRW[M, T](this, get, (s, u) => set(value, u))

  override def updated(newValue: M) = newValue
}

class ZoomModelRW[M, T](root: RootModelR[M], get: M => T, set: (M, T) => M) extends ZoomModelR(root, get) with ModelRW[M, T] {
  override def zoomRW[U](get: T => U)(set: (T, U) => T) =
    new ZoomModelRW[M, U](root, get compose this.get, (s, u) => this.set(s, set(this.get(s), u)))

  override def updated(newValue: T) = set(root.value, newValue)
}
