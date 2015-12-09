package diode.util

import diode.ActionResult._
import diode._
import diode.util.PotState._
import utest._

import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global

object PotActionTests extends TestSuite {

  case class TestAction(value: Pot[String] = Empty) extends PotAction[String, TestAction] {
    override def next(newValue: Pot[String]) = TestAction(newValue)
  }

  case class TestCollAction(value: Pot[Set[(String, Pot[String])]] = Empty) extends PotAction[Set[(String, Pot[String])], TestCollAction] {
    override def next(newValue: Pot[Set[(String, Pot[String])]]) = TestCollAction(newValue)
  }

  case class Model(s: Pot[String])

  case class CollModel(c: PotMap[String, Pot[String]])

  class TestHandler[M](modelRW: ModelRW[M, Pot[String]]) extends ActionHandler(modelRW) {
    override def handle = {
      case action: TestAction =>
        val updateF = action.effect(Future(42))(_.toString)
        action.handleWith(this, updateF)(PotAction.handler())
    }
  }

  class TestCollHandler[M](modelRW: ModelRW[M, PotMap[String, Pot[String]]], keys: Set[String]) extends ActionHandler(modelRW) {
    override def handle = {
      case action: TestCollAction =>
        val updateF = action.effect(Future(42))(v => keys.map(k => (k, Ready(v.toString))))
        action.handleWith(this, updateF)(PotAction.mapHandler(keys))
    }
  }

  class TestFailHandler[M](modelRW: ModelRW[M, Pot[String]]) extends ActionHandler(modelRW) {
    override def handle = {
      case action: TestAction =>
        val updateF = action.effect(Future(throw new TimeoutException))(_.toString)
        action.handleWith(this, updateF)(PotAction.handler())
    }
  }

  val fetcher = new Fetch[String] {
    override def fetch(key: String): Unit = ()
    override def fetch(start: String, end: String): Unit = ()
    override def fetch(keys: Traversable[String]): Unit = ()
  }

  def tests = TestSuite {
    'PotAction - {
      'CreateEmpty - {
        val ta = TestAction()
        assert(ta.value.isEmpty)
      }
      'Stages - {
        val ta = TestAction()
        val p = ta.pending
        assert(p.value.isPending)
        val f = p.failed(new IllegalArgumentException("Test"))
        assert(f.value.isFailed)
        assert(f.state == PotFailed)
        assert(f.value.exceptionOption.isDefined)
        val r = f.ready("Ready!")
        assert(r.value.isReady)
        assert(r.state == PotReady)
        assert(r.value.get == "Ready!")
      }
      'Effect - {
        val ta = TestAction()
        var completed = false
        val eff = ta.effect(Future {completed = !completed; 42})(_.toString)

        eff.toFuture.map { action =>
          assert(action.value == Ready("42"))
          assert(completed == true)
        }
      }
      'EffectFail - {
        val ta = TestAction()
        val eff = ta.effect(Future {if (true) throw new Exception("Oh no!") else 42})(_.toString, ex => new Exception(ex.getMessage * 2))

        eff.toFuture.map { action =>
          assert(action.value.exceptionOption.exists(_.getMessage == "Oh no!Oh no!"))
        }
      }
    }

    'PotActionHandler - {
      val model = Model(Ready("41"))
      val modelRW = new RootModelRW(model)
      val handler = new TestHandler(modelRW.zoomRW(_.s)((m, v) => m.copy(s = v)))
      val handlerFail = new TestFailHandler(modelRW.zoomRW(_.s)((m, v) => m.copy(s = v)))
      'PotEmptyOK - {
        val nextAction = handler.handle(TestAction()) match {
          case ModelUpdateEffect(newModel, effects) =>
            assert(newModel.s.isPending)
            assert(effects.size == 1)
            // run effect
            effects.toFuture
          case _ =>
            ???
        }
        nextAction.map {
          case TestAction(value) if value.isReady =>
            assert(value.get == "42")
          case _ => assert(false)
        }
      }
      'PotEmptyFail - {
        val nextAction = handlerFail.handle(TestAction()) match {
          case ModelUpdateEffect(newModel, effects) =>
            assert(newModel.s.isPending)
            assert(effects.size == 1)
            // run effect
            effects.toFuture
          case _ =>
            ???
        }
        nextAction.map {
          case TestAction(value) if value.isFailed =>
            assert(value.exceptionOption.get.isInstanceOf[TimeoutException])
          case _ => assert(false)
        }
      }
      'PotFailed - {
        handler.handle(TestAction(Failed(new Exception("Oh no!")))) match {
          case ModelUpdate(newModel) =>
            assert(newModel.s.isFailed)
            assert(newModel.s.exceptionOption.get.getMessage == "Oh no!")
          case _ => assert(false)
        }
      }
      'PotFailedRetry - {
        val model = Model(PendingStale("41", Retry(1)))
        val modelRW = new RootModelRW(model)
        val handlerFail = new TestFailHandler(modelRW.zoomRW(_.s)((m, v) => m.copy(s = v)))
        val nextAction = handlerFail.handle(TestAction(Failed(new TimeoutException))) match {
          case ModelUpdateEffect(newModel, effects) =>
            assert(newModel.s.isPending)
            assert(newModel.s.nonEmpty)
            assert(newModel.s.isInstanceOf[PendingStale[String]])
            assert(effects.size == 1)
            // run effect
            effects.toFuture
          case _ =>
            ???
        }
        nextAction.map {
          case TestAction(value) if value.isFailed =>
            assert(value.exceptionOption.get.isInstanceOf[TimeoutException])
          case _ => assert(false)
        }
      }
    }
    'CollectionHandler - {
      val model = CollModel(new PotMap[String, Pot[String]](fetcher))
      val modelRW = new RootModelRW(model)
      val handler = new TestCollHandler(modelRW.zoomRW(_.c)((m, v) => m.copy(c = v)), Set("A"))
      'empty - {
        val nextAction = handler.handle(TestCollAction()) match {
          case ModelUpdateEffect(newModel, effects) =>
            assert(newModel.c("A").isPending)
            assert(effects.size == 1)
            // run effect
            effects.toFuture
          case _ =>
            ???
        }
        nextAction.map {
          case TestCollAction(value) if value.isReady =>
            assert(value.get == Set(("A", Ready("42"))))
          case _ => assert(false)
        }
      }
    }
  }
}
