package diode

import java.util.concurrent.atomic.AtomicInteger

import utest._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._

object EffectTests extends TestSuite {
  import AnyAction._

  def tests = TestSuite {
    def efA = Effect(Future("A"))
    def efB = Effect(Future("B"))
    def efC = Effect(Future("C"))
    def efD = Effect.action("D")
    def efE = Effect.action("E")

    "Effect" - {
      "run" - {
        var x = ""
        efA.run(y => x = y.asInstanceOf[String]).map { _ =>
          assert(x == "A")
        }
      }
      "toFuture" - {
        efA.toFuture.map { z =>
          assert(z == "A")
        }
      }
      "map" - {
        efA.map(x => s"$x$x").toFuture.map { z =>
          assert(z == "AA")
        }
      }
      "flatMap" - {
        efA.flatMap(x => Future(s"$x$x")).toFuture.map { z =>
          assert(z == "AA")
        }
      }
      "after" - {
        val now = System.currentTimeMillis()
        efA.after(100.milliseconds)(diode.Implicits.runAfterImpl).map(x => s"$x$x").toFuture.map { z =>
          assert(z == "AA")
          assert(System.currentTimeMillis() - now > 80)
        }
      }
      "+" - {
        val e  = efA + efB
        val ai = new AtomicInteger(0)
        e.run(x => ai.incrementAndGet()).map { _ =>
          assert(ai.intValue() == 2)
        }
      }
      ">>" - {
        val e = efA >> efB >> efC
        var r = List.empty[String]
        e.run(x => r = r :+ x.asInstanceOf[String]).map { _ =>
          assert(r == List("A", "B", "C"))
        }
      }
      "<<" - {
        val e = efA << efB << efC
        var r = List.empty[String]
        e.run(x => r = r :+ x.asInstanceOf[String]).map { _ =>
          assert(r == List("C", "B", "A"))
        }
      }
    }
    "EffectSeq" - {
      "map" - {
        val e = efA >> efB >> efC
        assert(e.size == 3)
        e.map(x => s"$x$x").toFuture.map { z =>
          assert(z == "CC")
        }
      }
      "flatMap" - {
        val e = efA >> efB >> efC
        assert(e.size == 3)
        e.flatMap(x => Future(s"$x$x")).toFuture.map { z =>
          assert(z == "CC")
        }
      }
      "complex" - {
        val e = (efA + efB) >> efC
        assert(e.size == 3)
        e.map(x => s"$x$x").toFuture.map { z =>
          assert(z == "CC")
        }

      }
    }
    "EffectSet" - {
      "map" - {
        val e = efA + efB + efC
        println(s"size = ${e.size}")
        assert(e.size == 3)
        e.map(x => s"$x$x").toFuture.map { z =>
          assert(z == Set("AA", "BB", "CC"))
        }
      }
      "flatMap" - {
        val e = efA + efB + efC
        println(s"size = ${e.size}")
        assert(e.size == 3)
        e.flatMap(x => Future(s"$x$x")).toFuture.map { z =>
          assert(z == Set("AA", "BB", "CC"))
        }
      }
      "complex" - {
        val e = (efA >> efB) + efC
        assert(e.size == 3)
        e.map(x => s"$x$x").toFuture.map { z =>
          assert(z == Set("BB", "CC"))
        }
      }
    }
    "EffectAction" - {
      "run" - {
        var x = ""
        efD.run(y => x = y.asInstanceOf[String]).map { _ =>
          assert(x == "D")
        }
      }
      "toFuture" - {
        efD.toFuture.map { z =>
          assert(z == "D")
        }
      }
      "map" - {
        efD.map(x => s"$x$x").toFuture.map { z =>
          assert(z == "DD")
        }
      }
      "flatMap" - {
        efD.flatMap(x => Future(s"$x$x")).toFuture.map { z =>
          assert(z == "DD")
        }
      }
      "after" - {
        val now = System.currentTimeMillis()
        efD.after(100.milliseconds)(diode.Implicits.runAfterImpl).map(x => s"$x$x").toFuture.map { z =>
          assert(z == "DD")
          assert(System.currentTimeMillis() - now > 80)
        }
      }
      "+" - {
        val e  = efA + efD
        val ai = new AtomicInteger(0)
        e.run(x => ai.incrementAndGet()).map { _ =>
          assert(ai.intValue() == 2)
        }
      }
      ">>" - {
        val e = efA >> efB >> efC >> efD >> efE
        var r = List.empty[String]
        e.run(x => r = r :+ x.asInstanceOf[String]).map { _ =>
          assert(r == List("A", "B", "C", "D", "E"))
        }
      }
      "<<" - {
        val e = efA << efB << efC << efD << efE
        var r = List.empty[String]
        e.run(x => r = r :+ x.asInstanceOf[String]).map { _ =>
          assert(r == List("E", "D", "C", "B", "A"))
        }
      }
    }
  }
}
