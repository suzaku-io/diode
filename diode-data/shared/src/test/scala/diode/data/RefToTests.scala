package diode.data

import diode.{Action, RootModelRW}
import utest._

object RefToTests extends TestSuite {

  case class Model(users: PotMap[String, User], employees: Seq[Employee])
  case class ModelV(users: PotVector[User], employees: Seq[Employee])
  case class ModelS(users: PotStream[String, Pot[User]], employees: Seq[Employee])

  case class User(name: String)

  case class Employee(role: String, user: RefTo[Pot[User]])

  class TestFetcher[K] extends Fetch[K] {
    var lastFetch: Any                             = _
    override def fetch(key: K): Unit               = lastFetch = key
    override def fetch(start: K, end: K): Unit     = lastFetch = (start, end)
    override def fetch(keys: Traversable[K]): Unit = lastFetch = keys
  }

  case class RefAction(s: String) extends Action

  def tests = TestSuite {
    'refToMap - {
      val fetcher = new TestFetcher[String]
      val root    = Model(PotMap(fetcher, Map("ceoID" -> Ready(User("Ms. CEO")))), Seq())
      val modelRW = new RootModelRW(root)
      val m = root.copy(
        employees = Seq(Employee("CEO", RefTo("ceoID", modelRW.zoom(_.users))((id, value) => RefAction(s"Update $id to $value")))))
      assert(m.employees.head.user().get.name == "Ms. CEO")
      assert(m.employees.head.user.updated(Ready(User("Ms. Kathy CEO"))) == RefAction("Update ceoID to Ready(User(Ms. Kathy CEO))"))
    }
    'refToVector - {
      val fetcher = new TestFetcher[Int]
      val root    = ModelV(PotVector(fetcher, 5, Vector(Ready(User("Ms. CEO")))), Seq())
      val modelRW = new RootModelRW(root)
      val m =
        root.copy(employees = Seq(Employee("CEO", RefTo(0, modelRW.zoom(_.users))((id, value) => RefAction(s"Update $id to $value")))))
      assert(m.employees.head.user().get.name == "Ms. CEO")
      assert(m.employees.head.user.updated(Ready(User("Ms. Kathy CEO"))) == RefAction("Update 0 to Ready(User(Ms. Kathy CEO))"))
    }
    'refToStream - {
      val fetcher = new TestFetcher[String]
      val root    = ModelS(PotStream(fetcher, Seq("ceoID" -> Ready(User("Ms. CEO")))), Seq())
      val modelRW = new RootModelRW(root)
      val m = root.copy(
        employees = Seq(Employee("CEO", RefTo.stream("ceoID", modelRW.zoom(_.users))((id, value) => RefAction(s"Update $id to $value")))))
      assert(m.employees.head.user().get.name == "Ms. CEO")
      assert(m.employees.head.user.updated(Ready(User("Ms. Kathy CEO"))) == RefAction("Update ceoID to Ready(User(Ms. Kathy CEO))"))
    }
  }
}
