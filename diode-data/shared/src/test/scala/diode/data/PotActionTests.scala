package diode.data

import diode.ActionResult._
import diode._
import diode.data.PotState._
import diode.util.Retry.Immediate
import diode.util.{RetryPolicy, Retry}
import diode.Implicits.runAfterImpl
import utest._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.util.{Failure, Try}

object PotActionTests extends TestSuite {
  case class TestAction(potResult: Pot[String] = Empty) extends PotAction[String, TestAction] {
    override def next(newResult: Pot[String]) = TestAction(newResult)
  }

  case class TestActionRP(potResult: Pot[String] = Empty, retryPolicy: RetryPolicy = Retry.None)
      extends PotActionRetriable[String, TestActionRP] {
    override def next(newResult: Pot[String], newRetryPolicy: RetryPolicy) = TestActionRP(newResult, newRetryPolicy)
  }

  case class TestCollAction(state: PotState = PotState.PotEmpty,
                            result: Try[Set[(String, Pot[String])]] = Failure(new AsyncAction.PendingException))
      extends AsyncAction[Set[(String, Pot[String])], TestCollAction] {
    override def next(newState: PotState, newResult: Try[Set[(String, Pot[String])]]) =
      TestCollAction(newState, newResult)
  }

  case class Model(s: Pot[String])

  case class CollModel(c: PotMap[String, String])

  class TestHandler[M](modelRW: ModelRW[M, Pot[String]]) extends ActionHandler(modelRW) {
    override def handle = {
      case action: TestAction =>
        val updateF = action.effect(Future(42))(_.toString)
        action.handleWith(this, updateF)(PotAction.handler())
    }
  }

  class TestCollHandler[M](modelRW: ModelRW[M, PotMap[String, String]], keys: Set[String]) extends ActionHandler(modelRW) {
    override def handle = {
      case action: TestCollAction =>
        val updateF = action.effect(Future(42))(v => keys.map(k => (k, Ready(v.toString))))
        action.handleWith(this, updateF)(AsyncAction.mapHandler(keys))
    }
  }

  class TestFailHandler[M](modelRW: ModelRW[M, Pot[String]]) extends ActionHandler(modelRW) {
    override def handle = {
      case action: TestActionRP =>
        val updateF = action.effectWithRetry(Future[Int](throw new TimeoutException))(_.toString)
        action.handleWith(this, updateF)(PotActionRetriable.handler())
      case action: TestAction =>
        val updateF = action.effect(Future[Int](throw new TimeoutException))(_.toString)
        action.handleWith(this, updateF)(PotAction.handler())
    }
  }

  val fetcher = new Fetch[String] {
    override def fetch(key: String): Unit                = ()
    override def fetch(start: String, end: String): Unit = ()
    override def fetch(keys: Traversable[String]): Unit  = ()
  }

  def tests = TestSuite {
    'PotAction - {
      'CreateEmpty - {
        val ta = TestAction()
        assert(ta.potResult.isEmpty)
      }
      'Stages - {
        val ta = TestAction()
        val p  = ta.pending
        assert(p.potResult.isPending)
        val f = p.failed(new IllegalArgumentException("Test"))
        assert(f.potResult.isFailed)
        assert(f.state == PotFailed)
        assert(f.potResult.exceptionOption.isDefined)
        val r = f.ready("Ready!")
        assert(r.potResult.isReady)
        assert(r.state == PotReady)
        assert(r.potResult.get == "Ready!")
      }
      'Effect - {
        val ta        = TestAction()
        var completed = false
        val eff       = ta.effect(Future { completed = !completed; 42 })(_.toString)

        eff.toFuture.map { action =>
          assert(action.potResult == Ready("42"))
          assert(completed == true)
        }
      }
      'EffectFail - {
        val ta  = TestAction()
        val eff = ta.effect(Future { if (true) throw new Exception("Oh no!") else 42 })(_.toString, ex => new Exception(ex.getMessage * 2))

        eff.toFuture.map { action =>
          assert(action.potResult.exceptionOption.exists(_.getMessage == "Oh no!Oh no!"))
        }
      }
    }

    'PotActionHandler - {
      val model       = Model(Ready("41"))
      val modelRW     = new RootModelRW(model)
      val handler     = new TestHandler(modelRW.zoomRW(_.s)((m, v) => m.copy(s = v)))
      val handlerFail = new TestFailHandler(modelRW.zoomRW(_.s)((m, v) => m.copy(s = v)))
      'PotEmptyOK - {
        val nextAction = handler.handleAction(model, TestAction()) match {
          case Some(ModelUpdateEffect(newModel, effects)) =>
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
        val nextAction = handlerFail.handleAction(model, TestAction()) match {
          case Some(ModelUpdateEffect(newModel, effects)) =>
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
        handler.handleAction(model, TestAction(Failed(new Exception("Oh no!")))) match {
          case Some(ModelUpdate(newModel)) =>
            assert(newModel.s.isFailed)
            assert(newModel.s.exceptionOption.get.getMessage == "Oh no!")
          case _ => assert(false)
        }
      }
      'PotFailedRetry - {
        val model       = Model(PendingStale("41"))
        val modelRW     = new RootModelRW(model)
        val handlerFail = new TestFailHandler(modelRW.zoomRW(_.s)((m, v) => m.copy(s = v)))
        val nextAction = handlerFail.handleAction(model, TestActionRP(Failed(new TimeoutException), Immediate(1))) match {
          case Some(EffectOnly(effects)) =>
            assert(effects.size == 1)
            // run effect
            effects.toFuture
          case _ =>
            ???
        }
        nextAction.map {
          case TestActionRP(value, _) if value.isFailed =>
            assert(value.exceptionOption.get.isInstanceOf[TimeoutException])
          case _ => assert(false)
        }
      }
    }
    'CollectionHandler - {
      val model   = CollModel(PotMap[String, String](fetcher))
      val modelRW = new RootModelRW(model)
      val handler = new TestCollHandler(modelRW.zoomRW(_.c)((m, v) => m.copy(c = v)), Set("A"))
      'empty - {
        val nextAction = handler.handleAction(model, TestCollAction()) match {
          case Some(ModelUpdateEffect(newModel, effects)) =>
            assert(newModel.c("A").isPending)
            assert(effects.size == 1)
            // run effect
            effects.toFuture
          case _ =>
            ???
        }
        nextAction.map {
          case TestCollAction(state, value) if state == PotState.PotReady =>
            assert(value.get == Set(("A", Ready("42"))))
          case _ => assert(false)
        }
      }
    }
  }
}
