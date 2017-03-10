package diode.data

import diode.{ActionHandler, ModelRW, Dispatcher}
import diode.data.PotState._
import diode.Implicits.runAfterImpl
import utest._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util._

object PotCollectionTests extends TestSuite {

  class TestFetcher[K] extends Fetch[K] {
    var lastFetch: Any                             = _
    override def fetch(key: K): Unit               = lastFetch = key
    override def fetch(start: K, end: K): Unit     = lastFetch = (start, end)
    override def fetch(keys: Traversable[K]): Unit = lastFetch = keys
  }

  def tests = TestSuite {
    'PotMap - {
      'update - {
        val fetcher = new TestFetcher[String]
        val m       = PotMap[String, String](fetcher)
        val m1      = m + ("test" -> Ready("Yeaa"))

        assert(m1.size == 1)
        assert(m1.get("test") == Ready("Yeaa"))
        val m2 = m1.updated("test3", Failed(new IllegalArgumentException))
        assert(m2.size == 2)
        assert(m2.get("test3").isFailed)
        val m3 = m2.updated(Seq("test4" -> Ready("4"), "test5" -> Ready("5")))
        assert(m3.size == 4)
        assert(m3.get("test4") == Ready("4"))
        assert(m3.get("test5") == Ready("5"))
      }
      'get - {
        val fetcher = new TestFetcher[String]
        val m       = PotMap[String, String](fetcher)
        val m1      = m + ("test1" -> Ready("Yeaa"))
        assert(m1.get("test2").isPending)
        runAfterImpl
          .runAfter(10) {
            assert(fetcher.lastFetch == "test2")
          }
          .flatMap { _ =>
            assert(m1.get(Seq("test1", "test2", "test3")).values.map(_.state) == Seq(PotReady, PotPending, PotPending))
            runAfterImpl.runAfter(10) {
              assert(fetcher.lastFetch == Seq("test3", "test2"))
            }
          }
      }
    }
    'PotVector - {
      'update - {
        val fetcher = new TestFetcher[Int]
        val v       = PotVector[String](fetcher, 10)
        val v1      = v.updated(0, Ready("0"))
        assert(v1(0) == Ready("0"))
        assert(v1(1).isPending)
        intercept[IndexOutOfBoundsException](v1(10))
        val v2 = v1.updated(9, Ready("9"))
        assert(v2(0) == Ready("0"))
        assert(v2(1).isPending)
        assert(v2(9) == Ready("9"))
      }
      'iterator - {
        val fetcher = new TestFetcher[Int]
        val v       = PotVector[String](fetcher, 10)
        val it0     = v.iterator
        assert(!it0.hasNext)
        val v1 = v.updated(5, Ready("0")).updated(8, Ready("1"))
        val it = v1.iterator
        assert(it.hasNext)
        assert(it.next() == ((5, Ready("0"))))
        assert(it.hasNext)
        assert(it.next() == ((8, Ready("1"))))
        assert(!it.hasNext)
      }
    }
    'PotStream - {
      'append - {
        val fetcher = new TestFetcher[String]
        val ps      = PotStream[String, String](fetcher)
        val ps1     = ps.append(Seq("1" -> "test1"))
        assert(ps1.headKeyOption.contains("1") && ps1.lastKeyOption.contains("1"))
        val ps2 = ps1.append(Seq("2" -> "test2"))
        assert(ps2.headKeyOption.contains("1") && ps2.lastKeyOption.contains("2"))
        val ps3 = ps2.append(Seq("3" -> "test3", "4" -> "test4"))
        assert(ps3.headKeyOption.contains("1") && ps3.lastKeyOption.contains("4"))
        assert(ps3.elems.size == 4)
        assert(ps3.elems("1").nextKey.contains("2"))
        assert(ps3.elems("1").prevKey.isEmpty)
        assert(ps3.elems("2").nextKey.contains("3"))
        assert(ps3.elems("2").prevKey.contains("1"))
        assert(ps3.elems("3").nextKey.contains("4"))
        assert(ps3.elems("3").prevKey.contains("2"))
        assert(ps3.elems("4").nextKey.isEmpty)
        assert(ps3.elems("4").prevKey.contains("3"))
      }
      'prepend - {
        val fetcher = new TestFetcher[String]
        val ps      = PotStream[String, String](fetcher)
        val ps1     = ps.prepend(Seq("1" -> "test1"))
        assert(ps1.headKeyOption.contains("1") && ps1.lastKeyOption.contains("1"))
        val ps2 = ps1.prepend(Seq("2" -> "test2"))
        assert(ps2.headKeyOption.contains("2") && ps2.lastKeyOption.contains("1"))
        val ps3 = ps2.prepend(Seq("4" -> "test4", "3" -> "test3"))
        // println(s"${ps3.elems} - ${ps3.headKeyOption} - ${ps3.lastKeyOption}")
        assert(ps3.headKeyOption.contains("4") && ps3.lastKeyOption.contains("1"))
        assert(ps3.elems.size == 4)
        assert(ps3.elems("1").prevKey.contains("2"))
        assert(ps3.elems("1").nextKey.isEmpty)
        assert(ps3.elems("2").prevKey.contains("3"))
        assert(ps3.elems("2").nextKey.contains("1"))
        assert(ps3.elems("3").prevKey.contains("4"))
        assert(ps3.elems("3").nextKey.contains("2"))
        assert(ps3.elems("4").prevKey.isEmpty)
        assert(ps3.elems("4").nextKey.contains("3"))
      }
      'remove - {
        val fetcher = new TestFetcher[String]
        val ps      = PotStream(fetcher, Seq("1" -> "test1", "2" -> "test2", "3" -> "test3", "4" -> "test4"))
        val ps1     = ps.remove("2")
        assert(!ps1.contains("2"))
        assert(ps1.get("1").nextKey.contains("3"))
        val ps2 = ps1.remove("4")
        assert(!ps2.contains("2"))
        assert(ps2.get("3").nextKey.isEmpty)
        val ps3 = ps2.remove("3").remove("1")
        assert(!ps3.contains("3"))
        assert(!ps3.contains("1"))
        assert(ps3.headOption.isEmpty)
      }
      'seq - {
        val fetcher = new TestFetcher[String]
        val ps      = PotStream[String, String](fetcher)
        val seq0    = ps.seq
        assert(seq0.isEmpty)
        val ps1 = ps.append(Seq("1" -> "test1", "2" -> "test2", "3" -> "test3", "4" -> "test4"))
        val seq = ps1.seq
        assert(seq.size == 4)
        assert(seq.head._1 == "1")
        assert(seq.last._1 == "4")
      }
      'iterator - {
        val fetcher = new TestFetcher[String]
        val ps      = PotStream[String, String](fetcher)
        val it0     = ps.iterator
        assert(!it0.hasNext)
        val ps1 = ps.append(Seq("1" -> "test1", "2" -> "test2", "3" -> "test3", "4" -> "test4"))
        val it  = ps1.iterator
        assert(it.hasNext)
        val v1 = it.next()
        assert(v1._2 == "test1")
        assert(it.hasNext)
        val v2 = it.next()
        assert(v2._2.contains("test2"))
        assert(it.hasNext)
        val v3 = it.next()
        assert(v3._2.contains("test3"))
        assert(it.hasNext)
        val v4 = it.next()
        assert(v4._2.contains("test4"))
        assert(!it.hasNext)
        intercept[NoSuchElementException](it.next())
      }
      'map - {
        val fetcher = new TestFetcher[String]
        val ps      = PotStream(fetcher, Seq("1" -> "test1", "2" -> "test2", "3" -> "test3", "4" -> "test4"))
        val ps1     = ps.map((k, v) => if (k != "4") v + "x" else v)
        assert(ps1("1").endsWith("x"))
        assert(ps1("2").endsWith("x"))
        assert(ps1("3").endsWith("x"))
        assert(!ps1("4").endsWith("x"))
      }
    }
    'VerifyDocs - {
      case class User(id: String, name: String)

      // define a AsyncAction for updating users
      case class UpdateUsers(
          keys: Set[String],
          state: PotState = PotState.PotEmpty,
          result: Try[Map[String, Pot[User]]] = Failure(new AsyncAction.PendingException)
      ) extends AsyncAction[Map[String, Pot[User]], UpdateUsers] {
        def next(newState: PotState, newValue: Try[Map[String, Pot[User]]]) =
          UpdateUsers(keys, newState, newValue)
      }

      // an implementation of Fetch for users
      class UserFetch(dispatch: Dispatcher) extends Fetch[String] {
        override def fetch(key: String): Unit =
          dispatch(UpdateUsers(keys = Set(key)))
        override def fetch(keys: Traversable[String]): Unit =
          dispatch(UpdateUsers(keys = Set() ++ keys))
      }

      abstract class Handler[M](modelRW: ModelRW[M, PotMap[String, User]], keys: Set[String]) extends ActionHandler(modelRW) {

        // function to load a set of users based on keys
        def loadUsers(keys: Set[String]): Future[Map[String, Pot[User]]]

        // handle the action
        override def handle = {
          case action: UpdateUsers =>
            val updateEffect = action.effect(loadUsers(action.keys))(identity)
            action.handleWith(this, updateEffect)(AsyncAction.mapHandler(action.keys))
        }
      }
    }
  }
}
