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
      'fromOption - {
        assert(Pot.fromOption(Some("a")) == Ready("a"))
        val none: Option[String] = None
        assert(Pot.fromOption(none) == Empty)
      }
      'startTime - {
        val empty = Pot.empty[String]
        val startTime0 = Pot.currentTime - 1L
        val pend = empty.pending(startTime0)
        assert( pend.asInstanceOf[PendingBase].startTime == startTime0 )
        assert( pend.pending(startTime0).asInstanceOf[PendingBase].startTime == startTime0 )
        assert( pend.pending().asInstanceOf[PendingBase].startTime > startTime0 )
        val ready = pend.ready("a")
        assert( ready.pending(startTime0).asInstanceOf[PendingBase].startTime == startTime0 )
        assert( ready.pending().asInstanceOf[PendingBase].startTime != startTime0 )
        val fail = ready.fail(new Exception)
        assert( fail.pending(startTime0).asInstanceOf[PendingBase].startTime == startTime0 )
        assert( fail.pending().asInstanceOf[PendingBase].startTime > startTime0 )
      }
    }
  }
}
