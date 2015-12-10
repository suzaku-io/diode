package diode.data

import diode.RootModelRW
import utest._

object RefToTests extends TestSuite {

  case class Model(users: PotMap[String, User], employees: Seq[Employee])

  case class User(name: String)

  case class Employee(role: String, user: RefTo[Pot[User]])

  class TestFetcher[K] extends Fetch[K] {
    var lastFetch: Any = _
    override def fetch(key: K): Unit = lastFetch = key
    override def fetch(start: K, end: K): Unit = lastFetch = (start, end)
    override def fetch(keys: Traversable[K]): Unit = lastFetch = keys
  }

  def tests = TestSuite {
    'refToMap - {
      val fetcher = new TestFetcher[String]
      val root = Model(PotMap(fetcher, Map("ceoID" -> Ready(User("Ms. CEO")))), Seq())
      val modelRW = new RootModelRW[Model](root)
      val m = root.copy(employees = Seq(Employee("CEO", RefTo("ceoID", modelRW.zoom(_.users))((id, value) => s"Update $id to $value"))))
      assert(m.employees.head.user().get.name == "Ms. CEO")
      assert(m.employees.head.user.updateAction(Ready(User("Ms. Kathy CEO"))) == "Update ceoID to Ready(User(Ms. Kathy CEO))")
    }
  }
}
