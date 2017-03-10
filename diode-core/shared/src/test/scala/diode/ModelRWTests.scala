package diode

import utest._

object ModelRWTests extends TestSuite {

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

  def tests = TestSuite {
    'eq - {
      var m  = ComplexModel(A(999, "string"), B(Seq(1.0f, 2.0f, 3.0f), 3.0f), C(42, "c", Some(A(998, "A"))))
      val mr = new RootModelR(m)
      // use ref eq for "A"
      val r1 = mr.zoom(_.a)
      assert(!(r1 === A(999, "string")))
      // use value eq for "Partial" class
      val r2 = mr.zoom(m => Partial(m.a.i, m.c.s))
      assert(r2 === Partial(999, "c"))
      // use value eq for values
      val r3 = mr.zoom(_.a.i)
      val r4 = mr.zoom(_.a.s)
      assert(r3 === 999)
      assert(r4 === "string")
      val oldA = r1()
      val oldP = r2()
      m = m.copy(a = A(999, "string"))
      assert(r3 === 999)
      assert(r4 === "string")
      // reference changes, non equal
      assert(!(r1 === oldA))
      // reference changes, still equal
      assert(r2 === oldP)

      // need explicit import due to implicit priorities
      import PartialToo._
      val r5    = mr.zoom(m => PartialToo(m.c.s, m.a.i))
      val oldPT = r5()
      m = m.copy(a = A(9, "str"))
      // use custom FastEq
      assert(r5 === oldPT)
    }
    'zip - {
      var m  = Model(A(42, "test"), B(Seq(1, 2, 3), 4))
      val mr = new RootModelR(m)
      val r1 = mr.zoom(_.a)
      val r2 = mr.zoom(_.a.i)
      val r3 = mr.zoom(_.b.max)
      val r4 = mr.zoom(_.b.fs)

      val z1 = r1.zip(r4)
      val z2 = r2.zip(r3)

      val v1 = z1.value
      assert(z1.value == ((m.a, m.b.fs)))
      val v2 = z2.value
      assert(z2.value == ((m.a.i, m.b.max)))

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
    'map - {
      'equality - {
        var m  = ModelOpt(1.0f, Some(C(4242, "some", Some(A(66, "deep")))))
        val mr = new RootModelR(m)
        val cr = mr.zoom(_.c)

        val r1  = mr.zoom(_.c.map(_.s))
        val rv1 = r1.value
        // normal zoomed values change reference on each call
        assert(rv1 ne r1.value)
        val rm1  = cr.map((c: C) => c.s)
        val rmi1 = mr.zoomMap(_.c)(_.i)
        // mapped values maintain reference
        assert(rm1.value eq rm1.value)
        assert(rmi1.value eq rmi1.value)
        val rmv1  = rm1.value
        val rmiv1 = rmi1.value
        m = m.copy(c = m.c.map(c => c.copy(i = 4243)))
        assert(rv1 ne r1.value)
        // string s did not change
        assert(rmv1 eq rm1.value)
        // int i did change
        assert(rmiv1 ne rmi1.value)
        m = m.copy(c = m.c.map(c => c.copy(s = "else")))
        // string s did change
        assert(rmv1 ne rm1.value)
      }
      'flatMap - {
        var m   = ModelOpt(1.0f, Some(C(4242, "some", Some(A(66, "deep")))))
        val mr  = new RootModelR(m)
        val cr  = mr.zoomFlatMap(_.c)(_.o)
        val crs = cr.map((a: A) => a.s)
        val v1  = crs.value
        println(v1)
        assert(v1 eq crs.value)
        m = m.copy(c = m.c.map(c => c.copy(o = c.o.map(_.copy(i = 0)))))
        assert(v1 eq crs.value)
        m = m.copy(c = m.c.map(c => c.copy(o = c.o.map(_.copy(s = "deeper")))))
        assert(v1 ne crs.value)
      }
      'docExample - {
        // just making sure the code in docs compiles :)
        case class Root(a: A, b: B, c: String)
        case class A(d: Int, e: String)
        case class B(f: Boolean, g: Option[D])
        case class D(h: Seq[Int], i: Int)

        val root = Root(A(42, "42"), B(true, Some(D(Seq(42, 42, 42), 43))), "c")
        object AppCircuit extends Circuit[Root] {
          override protected def initialModel: Root                        = root
          override protected def actionHandler: AppCircuit.HandlerFunction = ???
        }
        'ex1 - {
          val reader: ModelR[Root, String] = new RootModelR(root).zoom(_.a.e)
        }
        'ex2 - {
          val reader: ModelR[Root, String] = AppCircuit.zoom(_.a.e)
        }
        'ex3 - {
          val reader: ModelR[Root, Option[Seq[Int]]] = AppCircuit.zoom(_.b.g.map(_.h))
          assert(reader.value ne reader.value)
        }
        'ex4 - {
          val reader: ModelR[Root, Option[Seq[Int]]] = AppCircuit.zoomMap(_.b.g)(_.h)
          assert(reader.value eq reader.value)
        }
        'ex5 - {
          val complexReader: ModelR[Root, (String, Boolean)] = AppCircuit.zoom(r => (r.a.e, r.b.f))
        }
        'ex6 - {
          val reader: ModelR[Root, String]           = AppCircuit.zoom(_.a.e)
          val zipReader: ModelR[Root, (String, Int)] = reader.zip(AppCircuit.zoom(_.a.d))
        }
        'ex7 - {
          case class FromAB(e: String, g: Option[D]) extends UseValueEq
          val abReader: ModelR[Root, FromAB] =
            AppCircuit.zoom(r => FromAB(r.a.e, r.b.g))
          assert(abReader === FromAB("42", Some(D(Seq(42, 42, 42), 43))))
        }
        'ex8 - {
          case class ExtData(age: Int, name: String)

          implicit object extDataEq extends FastEq[ExtData] {
            override def eqv(a: ExtData, b: ExtData): Boolean = a == b
          }

          val extReader: ModelR[Root, ExtData] =
            AppCircuit.zoom(r => ExtData(r.a.d, r.c))

          assert(extReader === ExtData(42, "c"))

          val valReader: ModelR[Root, String] =
            AppCircuit.zoom(r => s"${r.a.d}:${r.a.e}")(FastEq.ValueEq)

          assert(valReader === "42:42")
        }
      }
    }
    'mapRW - {
      val m   = ModelOpt(1.0f, Some(C(4242, "some", Some(A(66, "deep")))))
      val mrw = new RootModelRW(m)
      val crw = mrw.zoomFlatMapRW(_.c)(_.o)((m, o) => m.copy(c = m.c.map(c => c.copy(o = o))))
      assert(crw.value eq crw.value)
      val m1 = crw.updated(None)
      assert(m1.c.flatMap(_.o).isEmpty)
      val m2 = crw.updated(Some(A(42, "new")))
      assert(m2.c.flatMap(_.o.map(_.s)).contains("new"))
    }
    'zoomRW - {
      val m      = A(1, "old")
      val mrw    = new RootModelRW(m)
      val zoomRW = mrw.zoomRW(_.i)((m, v) ⇒ m.copy(i = v))
      val r      = zoomRW.updatedWith(m.copy(s = "new"), 2)
      assert(r.i == 2)
      assert(r.s == "new")
    }
    'zoomTo - {
      val m      = A(1, "old")
      val mrw    = new RootModelRW(m)
      val zoomRW = mrw.zoomTo(_.i)
      val r      = zoomRW.updatedWith(m.copy(s = "new"), 2)
      assert(r.i == 2)
      assert(r.s == "new")
    }
    'zoomToLong - {
      val m   = Model(A(1, "old"), B(Seq(1.0f), 5.0f))
      val mrw = new RootModelRW(m)
      // val zoomRW = mrw.zoomRW(_.a)((m, v) ⇒ m.copy(a = v)).zoomRW(_.i)((m, v) ⇒ m.copy(i = v))
      val zoomTo = mrw.zoomTo(_.a.i)
      val r        = zoomTo.updatedWith(m, 2)
      assert(r.a.i == 2)
      assert(r.b.max == 5.0f)
    }
  }
}
