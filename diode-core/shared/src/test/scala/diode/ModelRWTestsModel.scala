package diode

object ModelRWTestsModel {
  case class Model(a: A, b: B)

  case class ModelOpt(x: Float, c: Option[C])

  case class A(i: Int, s: String)

  case class B(fs: Seq[Float], max: Float)

  case class C(i: Int, s: String, o: Option[A])

  case class ComplexModel(a: A, b: B, c: C)

  case class Partial(i: Int, s: String) extends UseValueEq

  case class PartialToo(s: String, i: Int)

  object PartialToo {
    implicit object partialEq extends FastEq[PartialToo] {
      override def eqv(a: PartialToo, b: PartialToo): Boolean = a.s == b.s
    }
  }
}
