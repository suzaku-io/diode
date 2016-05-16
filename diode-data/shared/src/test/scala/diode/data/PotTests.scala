package diode.data

import utest._

object PotTests extends TestSuite {
  def tests = TestSuite {
    'Pot - {
      'mapVariants - {
        val p = Pot.empty[String]
        assert(p.map(_.length) == Empty)
        assert(p.ready("test").map(_.length) == Ready(4))
        assert(p.pending().map(_.length).isInstanceOf[Pending])
        assert(p.fail(new Exception("Boo!")).map(_.length).isInstanceOf[Failed])
        val fs = p.ready("test").fail(new Exception("Boo!")).map(_.length)
        assert(fs.contains(4) && fs.isFailed)
        val ps = p.ready("test").pending(50).map(_.length)
        assert(ps.contains(4) && ps.isPending)
      }
      'flatMapVariants - {
        val p = Pot.empty[String]
        assert(p.flatMap(s => Ready(s.length)) == Empty)
        assert(p.ready("test").flatMap(s => Ready(s.length)) == Ready(4))
        assert(p.pending().flatMap(s => Ready(s.length)).isInstanceOf[Pending])
        assert(p.fail(new Exception("Boo!")).flatMap(s => Ready(s.length)).isInstanceOf[Failed])
        val fs = p.ready("test").fail(new Exception("Boo!")).flatMap(s => Ready(s.length))
        assert(fs.contains(4))
        val ps = p.ready("test").pending(50).flatMap(s => Ready(s.length))
        assert(ps.contains(4) && ps.isPending)
      }
    }
  }
}
