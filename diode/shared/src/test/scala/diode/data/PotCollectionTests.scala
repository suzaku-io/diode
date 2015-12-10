package diode.data

import diode.data.PotState._
import utest._

object PotCollectionTests extends TestSuite {
  class TestFetcher[K] extends Fetch[K] {
    var lastFetch:Any = _
    override def fetch(key: K): Unit = lastFetch = key
    override def fetch(start: K, end: K): Unit = lastFetch = (start, end)
    override def fetch(keys: Traversable[K]): Unit = lastFetch = keys
  }
  def tests = TestSuite {
    'PotMap - {
      'update - {
        val fetcher = new TestFetcher[String]
        val m = PotMap[String, String](fetcher)
        val m1 = m + ("test" -> Ready("Yeaa"))

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
        val m = new PotMap[String, String](fetcher)
        val m1 = m + ("test1" -> Ready("Yeaa"))
        assert(m1.get("test2").isPending)
        assert(fetcher.lastFetch == "test2")
        assert(m1.get(Seq("test1", "test2", "test3")).values.map(_.state) == Seq(PotReady, PotPending, PotPending))
        assert(fetcher.lastFetch == Seq("test3", "test2"))
      }
    }
    'PotVector - {
      'update - {
        val fetcher = new TestFetcher[Int]
        val v = PotVector[String](fetcher, 10)
        val v1 = v.updated(0, Ready("0"))
        assert(v1(0) == Ready("0"))
        assert(v1(1).isPending)
        intercept[IndexOutOfBoundsException](v1(10))
        val v2 = v1.updated(9, Ready("9"))
        assert(v2(0) == Ready("0"))
        assert(v2(1).isPending)
        assert(v2(9) == Ready("9"))
      }
    }
  }
}
