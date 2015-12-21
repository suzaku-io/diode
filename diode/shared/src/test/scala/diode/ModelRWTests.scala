package diode

import utest._

object ModelRWTests extends TestSuite {

  case class Model(a: A, b: B)

  case class A(i: Int, s: String)

  case class B(fs: Seq[Float], max: Float)

  def tests = TestSuite {
    'zip - {
      var m = Model(A(42, "test"), B(Seq(1, 2, 3), 4))
      val mr = new RootModelR(m)
      val r1 = mr.zoom(_.a)
      val r2 = mr.zoom(_.a.i)
      val r3 = mr.zoom(_.b.max)
      val r4 = mr.zoom(_.b.fs)

      val z1 = r1.zip(r4)
      val z2 = r2.zip(r3)

      val v1 = z1.value
      assert(z1.value ==(m.a, m.b.fs))
      val v2 = z2.value
      assert(z2.value ==(m.a.i, m.b.max))

      m = m.copy(m.a.copy(s = "diode"))
      assert(v1 ne z1.value)
      // a.i and b have not changed
      assert(v2 eq z2.value)

      m = m.copy(b = m.b.copy(fs = Seq()))
      assert(v1 ne z1.value)
      // a.i and b.max have not changed
      assert(v2 eq z2.value)

      m = m.copy(b = m.b.copy(max = 44))
      // a.i and b.max have both changed
      assert(v2 ne z2.value)
    }
  }
}
