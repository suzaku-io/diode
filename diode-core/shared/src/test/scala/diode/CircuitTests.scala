package diode

import diode.ActionResult.ModelUpdate
import utest._

import scala.collection.mutable
import scala.concurrent._

object CircuitTests extends TestSuite {

  import AnyAction._
  import CircuitTestsModel._

  // actions
  case object NoChange extends diode.Action

  case class SetS(s: String) extends diode.Action

  case class SetSSilent(s: String) extends diode.Action

  case class SetD(d: Data) extends diode.Action

  case class SetEffect(s: String, effect: () => Future[AnyRef]) extends diode.Action

  case class SetEffectOnly(effect: () => Future[AnyRef]) extends diode.Action

  case class SetEffectActionOnly(action: Action) extends diode.Action

  case class SetEffectActionBatchOnly(effect: ActionBatch) extends diode.Action

  case class Delay(action: Any) extends diode.Action

  case class ThrowAction(ex: Throwable) extends diode.Action

  class AppCircuit(implicit val ec: ExecutionContext) extends Circuit[Model] {
    import diode.ActionResult._
    override def initialModel = Model("Testing", Data(42, true))
    override protected def actionHandler: HandlerFunction =
      (model, action) =>
        ({
          case SetS(s) =>
            ModelUpdate(model.copy(s = s))
          case SetSSilent(s) =>
            ModelUpdateSilent(model.copy(s = s))
          case SetEffectOnly(effect) =>
            EffectOnly(effect)
          case SetEffectActionOnly(action) =>
            EffectOnly(Effect.action(action))
          case SetEffectActionBatchOnly(actionBatch) =>
            EffectOnly(Effect.action(actionBatch))
          case SetD(d) =>
            ModelUpdate(model.copy(data = d))
          case SetEffect(s, effect) =>
            // run effect twice!
            ModelUpdateEffect(model.copy(s = s), Effect(effect()) + effect)
          case ThrowAction(ex) =>
            throw ex
        }: PartialFunction[Any, ActionResult[Model]]).lift.apply(action)

    var lastFatal: (Any, Throwable) = ("", null)
    var lastError                   = ""

    override def handleFatal(action: Any, e: Throwable): Unit = lastFatal = (action, e)
    override def handleError(msg: String): Unit               = lastError = msg
  }

  def tests = TestSuite {
    implicit val ec = ExecutionContext.global
    def circuit     = new AppCircuit

    "Dispatch" - {
      "Action" - {
        val c = circuit
        c.dispatch(SetS("New"))
        assert(c.model.s == "New")
      }
      "NoAction" - {
        val c = circuit
        c.dispatch(NoAction)
        assert(c.model.s == "Testing")
      }
      "Unknown" - {
        val c = circuit
        c.dispatch("Unknown")
        assert(c.model.s == "Testing")
        assert(c.lastError.contains("not handled"))
      }
      "ActionBatch" - {
        val c = circuit
        c.dispatch(ActionBatch(SetS("First"), SetD(Data(43, false)), SetS("Second")))
        assert(c.model.s == "Second")
        assert(c.model.data.i == 43)
        assert(c.model.data.b == false)
      }
      "NoHandler" - {
        val c = circuit
        case class TestMissing(i: Int)

        c.dispatch(TestMissing)
        assert(c.lastError.isEmpty == false)
      }
      "ErrorInHandler" - {
        val c = circuit
        c.dispatch(ThrowAction(new IllegalArgumentException("Oh noes!")))
        assert(c.lastFatal._2.isInstanceOf[IllegalArgumentException])
      }
    }
    "Zooming" - {
      "read" - {
        val c          = circuit
        val dataReader = c.zoom(_.data)
        assert(dataReader().i == 42)
        assert(dataReader().b == true)
      }
      "write" - {
        val c          = circuit
        val dataWriter = c.zoomRW(_.data)((m, v) => m.copy(data = v))
        val m          = dataWriter.updated(Data(43, false))
        assert(m.s == "Testing")
        assert(m.data.i == 43)
        assert(m.data.b == false)
      }
      "lens" - {
        val c          = circuit
        val dataWriter = c.zoomTo(_.data.i)
        val m          = dataWriter.updated(43)
        assert(m.s == "Testing")
        assert(m.data.i == 43)
      }
    }
    "Listener" - {
      "Normal" - {
        val c             = circuit
        var state: Model  = null
        var callbackCount = 0
        def listener(cursor: ModelRO[String]): Unit = {
          state = c.model
          callbackCount += 1
        }
        val unsubscribe = c.subscribe(c.zoom(_.s))(listener)
        c.dispatch(SetS("Listen"))
        assert(state.s == "Listen")
        assert(callbackCount == 1)
        // sequence of actions causes only a single callback
        c.dispatch(ActionBatch(SetS("Listen1"), SetS("Listen2"), SetS("Listen3"), SetS("Listen4")))
        assert(state.s == "Listen4")
        assert(callbackCount == 2)
        unsubscribe()
        c.dispatch(SetS("Deaf"))
        assert(state.s == "Listen4")
      }
      "Cursor" - {
        val c             = circuit
        var state: Model  = null
        var state2: Model = null
        var callbackCount = 0
        def listener1(cursor: ModelRO[Data]): Unit = {
          state = c.model
          callbackCount += 1
        }
        def listener2(cursor: ModelRO[String]): Unit = {
          state2 = c.model
          callbackCount += 1
        }
        c.subscribe(c.zoom(_.data))(listener1)
        c.subscribe(c.zoom(_.s))(listener2)
        // check that only listener2 is called
        c.dispatch(SetS("Listen"))
        assert(state == null)
        assert(state2.s == "Listen")
        assert(callbackCount == 1)
        // check that only listener1 is called
        c.dispatch(SetD(Data(43, false)))
        assert(state.data.i == 43)
        assert(state2.data.i == 42)
        assert(callbackCount == 2)
      }
      "Silent" - {
        val c             = circuit
        var state: Model  = null
        var callbackCount = 0
        def listener(cursor: ModelRO[String]): Unit = {
          state = c.model
          callbackCount += 1
        }
        val unsubscribe = c.subscribe(c.zoom(_.s))(listener)
        c.dispatch(SetSSilent("Listen"))
        assert(c.model.s == "Listen")
        // no callback called due to silent update
        assert(callbackCount == 0)
        unsubscribe()
      }
      "NestedSubscribe" - {
        val c                        = circuit
        var state1: Model            = null
        var state2: Model            = null
        var callback1Count           = 0
        var callback2Count           = 0
        var unsubscribe2: () => Unit = null

        def listener1(cursor: ModelRO[String]): Unit = {
          state1 = c.model
          callback1Count += 1

          if (unsubscribe2 == null) {
            unsubscribe2 = c.subscribe(c.zoom(_.s))(listener2)
          }
        }

        def listener2(cursor: ModelRO[String]): Unit = {
          state2 = c.model
          callback2Count += 1
        }

        val unsubscribe1 = c.subscribe(c.zoom(_.s))(listener1)
        c.dispatch(SetS("Listen"))
        assert(state1.s == "Listen")
        assert(callback1Count == 1)
        assert(state2 == null)
        assert(callback2Count == 0)

        // check listener2 was registered correctly during the last dispatch
        c.dispatch(SetS("Listen1"))
        assert(state1.s == "Listen1")
        assert(callback1Count == 2)
        assert(state2 != null)
        assert(state2.s == "Listen1")
        assert(callback2Count == 1)

        unsubscribe1()
        c.dispatch(SetS("Listen2"))
        assert(state1.s == "Listen1")
        assert(callback1Count == 2)
        assert(state2.s == "Listen2")
        assert(callback2Count == 2)

        unsubscribe2()
        c.dispatch(SetS("Listen3"))
        assert(state1.s == "Listen1")
        assert(callback1Count == 2)
        assert(state2.s == "Listen2")
        assert(callback2Count == 2)
      }
      "NestedUnsubscribe" - {
        val c                        = circuit
        var state1: Model            = null
        var state2: Model            = null
        var callback1Count           = 0
        var callback2Count           = 0
        var unsubscribe2: () => Unit = null
        var executeUnsubscribe2      = false

        def listener1(cursor: ModelRO[String]): Unit = {
          state1 = c.model
          callback1Count += 1

          if (executeUnsubscribe2) {
            unsubscribe2()
            unsubscribe2 = null
          }
        }

        def listener2(cursor: ModelRO[String]): Unit = {
          state2 = c.model
          callback2Count += 1
        }

        val sub: Subscriber[String] = c.subscribe(c.zoom(_.s))
        val _                       = sub(listener1)
        unsubscribe2 = sub(listener2)

        c.dispatch(SetS("Listen"))
        assert(state1.s == "Listen")
        assert(callback1Count == 1)
        assert(state2.s == "Listen")
        assert(callback2Count == 1)

        executeUnsubscribe2 = true

        c.dispatch(SetS("Listen1"))
        assert(state1.s == "Listen1")
        assert(callback1Count == 2)
        // Note that you can't reliably test for change or no change in state2 at this point
        // since listener execution order isn't necessarily equal to listener registration order

        executeUnsubscribe2 = false

        // Check that only listener1 is called
        val state2Snapshot         = state2
        val callback2CountSnapshot = callback2Count

        c.dispatch(SetS("Listen2"))
        assert(state1.s == "Listen2")
        assert(callback1Count == 3)
        assert(state2.s == state2Snapshot.s)
        assert(callback2Count == callback2CountSnapshot)
      }
      "ZippedSubscribe" - {

        val c                         = circuit
        var state1: (String, Boolean) = null
        var callback1Count            = 0

        val zoomS        = c.zoom(_.s)
        val zoomDataBool = c.zoom(_.data.b)
        val zipped       = zoomS zip zoomDataBool

        def listener(cursor: ModelRO[(String, Boolean)]): Unit = {
          state1 = cursor()
          callback1Count += 1
        }

        val unsubscribe = c.subscribe(zipped)(listener)

        // update on first zipped element should call te callback
        c.dispatch(SetS("Listen"))
        assert(state1._1 == "Listen")
        assert(callback1Count == 1)

        // Data.i is updated
        // but boolean is already true so the callback is not called
        val newData = Data(-42, state1._2)
        c.dispatch(SetD(newData))
        assert(state1._2 == newData.b)
        assert(callback1Count == 1)

        // boolean is changed so the callback is called
        val newData2 = newData.copy(b = !newData.b)
        c.dispatch(SetD(newData2))
        assert(state1._2 == newData2.b)
        assert(callback1Count == 2)

        unsubscribe()

        // unsubscribed so the listener is not called
        c.dispatch(SetD(newData2))
        assert(state1._2 == newData2.b)
        assert(callback1Count == 2)

      }
    }
    "Effects" - {
      "Run" - {
        val c         = circuit
        var effectRun = 0
        val effect    = SetEffect("Effect", () => { effectRun += 1; Future.successful(None) })
        c.dispatch(effect)
        assert(c.model.s == "Effect")
        assert(effectRun == 2)
      }
      "EffectOnly" - {
        val c         = circuit
        var effectRun = 0
        val effect    = SetEffectOnly(() => { effectRun += 1; Future.successful(None) })
        c.dispatch(effect)
        assert(effectRun == 1)
      }
      "EffectActionOnly" - {
        val c = circuit
        val effect = SetEffectActionOnly(SetS("Action1"))
        c.dispatch(effect)
        assert(c.model.s == "Action1")
      }
      "EffectActionBatchOnly" - {
        val c = circuit
        val ab = ActionBatch(SetS("Action2"), SetD(Data(100, true)))
        val effect = SetEffectActionBatchOnly(ab)
        c.dispatch(effect)
        assert(c.model.s == "Action2")
        assert(c.model.data.i == 100)
      }
      "EffectActionBatchOnlyNoChange" - {
        val c = circuit
        val ab = ActionBatch(NoChange, SetS("Action2"), SetD(Data(100, true)))
        val effect = SetEffectActionBatchOnly(ab)
        c.dispatch(effect)
        assert(c.model.s == "Action2")
        assert(c.model.data.i == 100)
      }
    }
    "Processor" - {
      "ModAction" - {
        val c = circuit
        val p = new ActionProcessor[Model] {
          override def process(dispatcher: Dispatcher,
                               action: Any,
                               next: Any => ActionResult[Model],
                               currentModel: Model
          ) = {
            next(action match {
              case s: String =>
                SetS(s)
              case _ => action
            })
          }
        }
        c.addProcessor(p)
        c.dispatch("Test")
        assert(c.model.s == "Test")
        c.removeProcessor(p)
        c.dispatch("Test2")
        assert(c.model.s == "Test")
      }
      "Filter" - {
        val c = circuit
        val p = new ActionProcessor[Model] {
          override def process(dispatcher: Dispatcher,
                               action: Any,
                               next: Any => ActionResult[Model],
                               currentModel: Model
          ) = {
            action match {
              case SetS(_) =>
                ActionResult.NoChange
              case _ => next(action)
            }
          }
        }
        c.addProcessor(p)
        c.dispatch(SetS("Test"))
        assert(c.model.s == "Testing")
      }
      "LogState" - {
        val c   = circuit
        var log = "log"
        val p = new ActionProcessor[Model] {
          override def process(dispatcher: Dispatcher,
                               action: Any,
                               next: Any => ActionResult[Model],
                               currentModel: Model
          ) = {
            next(action) match {
              case m: ModelUpdated[Model @unchecked] =>
                log += m.newModel.s
                m
              case r => r
            }
          }
        }
        c.addProcessor(p)
        c.dispatch(SetS("Test"))
        assert(log == "logTest")
      }
      "Delay" - {
        val c = circuit
        class AP extends ActionProcessor[Model] {
          val pending = mutable.Queue.empty[(Any, Dispatcher)]

          override def process(dispatcher: Dispatcher,
                               action: Any,
                               next: Any => ActionResult[Model],
                               currentModel: Model
          ) = {
            action match {
              case Delay(a) =>
                pending.enqueue((a, dispatcher))
                ActionResult.NoChange
              case _ => next(action)
            }
          }

          def run(): Unit = {
            pending.foreach { case (action, dispatcher) => dispatcher(action) }
            pending.clear()
          }
        }
        val p = new AP
        c.addProcessor(p)
        c.dispatch(Delay(SetS("Test")))
        assert(c.model.s == "Testing")
        p.run()
        assert(c.model.s == "Test")
      }
    }
    "FoldHandler" - {
      val c         = circuit
      val origModel = c.model
      val h1 = new ActionHandler[Model, Int](c.zoomRW(_.data.i)((m, t) => m.copy(data = m.data.copy(i = t)))) {
        override protected def handle = {
          case SetS(newS) =>
            updated(value + 1)
        }
      }
      val h2 = new ActionHandler[Model, String](c.zoomRW(_.s)((m, t) => m.copy(s = t))) {
        override protected def handle = {
          case SetS(newS) =>
            updated(value + newS.toUpperCase * 2)
        }
      }
      val fh = circuit.foldHandlers(h1, h2)

      val res = fh(c.model, SetS("test"))
      assert(res.contains(ModelUpdate(origModel.copy(s = "TestingTESTTEST", data = Data(43, true)))))
    }
    "NestedDispatch" - {
      val c = circuit
      // dispatch in a listener
      c.subscribe(c.zoom(_.s))(r => c.dispatch(SetD(Data(3, false))))
      c.dispatch(SetS("Nested"))
      assert(c.model.s == "Nested")
      assert(c.model.data == Data(3, false))
    }
  }
}
