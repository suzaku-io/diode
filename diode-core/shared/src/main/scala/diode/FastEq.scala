package diode

/**
  * Typeclass for fast equality comparison.
  */
trait FastEq[A] {
  def eqv(a: A, b: A): Boolean
  def neqv(a: A, b: A): Boolean = !eqv(a, b)
}

/**
  * Marker trait for readers to indicate the use of value equality instead of the default reference equality
  */
trait UseValueEq

/**
  * Implicit FastEq typeclass instances for AnyRef and AnyVal
  */
trait FastEqLowPri {

  // for any reference type, use reference equality check
  implicit object AnyRefEq extends FastEq[AnyRef] {
    override def eqv(a: AnyRef, b: AnyRef): Boolean = a eq b
  }

  // for any value type, use normal equality check
  implicit object AnyValEq extends FastEq[AnyVal] {
    override def eqv(a: AnyVal, b: AnyVal): Boolean = a == b
  }

  object ValueEq extends FastEq[Any] {
    override def eqv(a: Any, b: Any): Boolean = a == b
  }

}

/**
  * Implicit FastEq typeclass instance for `UseValueEq` marker trait
  */
object FastEq extends FastEqLowPri {

  // for classes extending marker trait `UseValueEq`, use normal equality check
  implicit object markerEq extends FastEq[UseValueEq] {
    override def eqv(a: UseValueEq, b: UseValueEq): Boolean = a == b
  }

}
