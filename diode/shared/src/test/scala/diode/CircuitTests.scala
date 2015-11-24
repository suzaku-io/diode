package diode

import diode.ActionResult.NoChange
import utest._

import scala.collection.mutable
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global

object CircuitTests extends TestSuite {

  // model
  case class Model(s: String, data: Data)

  case class Data(i: Int, b: Boolean)

  // actions
  case class SetS(s: String)

  case class SetD(d: Data)

  case class SetEffect(s: String, effect: () => Future[AnyRef])

  case class EffectOnly(effect: () => Future[AnyRef])

  case class Delay(action: AnyRef)

  class AppModel(implicit ec: ExecutionContext) extends Circuit[Model] {
    import diode.ActionResult._
    override var model = Model("Testing", Data(42, true))
    override protected def actionHandler: HandlerFunction = {
      case SetS(s) =>
        ModelUpdate(model.copy(s = s))
      case EffectOnly(effect) =>
        ModelUpdateEffect(model, Seq(effect), ec)
      case SetD(d) =>
        ModelUpdate(model.copy(data = d))
      case SetEffect(s, effect) =>
        // run effect twice!
        ModelUpdateEffectPar(model.copy(s = s), Seq(effect, effect), ec)
    }
    var lastFatal: (AnyRef, Throwable) = ("", null)
    var lastError = ""

    override def logFatal(action: AnyRef, e: Throwable): Unit = lastFatal = (action, e)
    override def logError(msg: String): Unit = lastError = msg
  }

  def tests = TestSuite {
    def circuit = new AppModel

    'Dispatch - {
      'Action - {
        val c = circuit
        c.dispatch(SetS("New"))
        assert(c.model.s == "New")
      }
      'None - {
        val c = circuit
        c.dispatch(None)
        assert(c.model.s == "Testing")
      }
      'Unknown - {
        val c = circuit
        c.dispatch("Unknown")
        assert(c.model.s == "Testing")
        assert(c.lastError.contains("not handled"))
      }
      'ActionSeq - {
        val c = circuit
        c.dispatch(Seq(SetS("First"), SetD(Data(43, false))))
        assert(c.model.s == "First")
        assert(c.model.data.i == 43)
        assert(c.model.data.b == false)
      }
    }
    'Zooming - {
      'read - {
        val c = circuit
        val dataReader = c.zoom(_.data)
        assert(dataReader.value.i == 42)
        assert(dataReader.value.b == true)
      }
      'write - {
        val c = circuit
        val dataWriter = c.zoomRW(_.data)((m, v) => m.copy(data = v))
        val m = dataWriter.update(Data(43, false))
        assert(m.s == "Testing")
        assert(m.data.i == 43)
        assert(m.data.b == false)
      }
    }
    'Listener - {
      val c = circuit
      var state: Model = null
      var callbackCount = 0
      def listener(): Unit = {
        state = c.model
        callbackCount += 1
      }
      val unsubscribe = c.subscribe(listener)
      c.dispatch(SetS("Listen"))
      assert(state.s == "Listen")
      assert(callbackCount == 1)
      // sequence of actions causes only a single callback
      c.dispatch(Seq(SetS("Listen1"), SetS("Listen2"), SetS("Listen3"), SetS("Listen4")))
      assert(state.s == "Listen4")
      assert(callbackCount == 2)
      unsubscribe()
      c.dispatch(SetS("Deaf"))
      assert(state.s == "Listen4")
    }
    'Effects - {
      'Run - {
        val c = circuit
        var effectRun = 0
        val effect = SetEffect("Effect", () => {effectRun += 1; Future.successful(None)})
        c.dispatch(effect)
        assert(c.model.s == "Effect")
        assert(effectRun == 2)
      }
      'EffectOnly - {
        val c = circuit
        var effectRun = 0
        val effect = EffectOnly(() => {effectRun += 1; Future.successful(None)})
        c.dispatch(effect)
        assert(effectRun == 1)
      }
    }
    'Processor - {
      'ModAction - {
        val c = circuit
        val p = new ActionProcessor[Model] {
          override def process(dispatcher: Dispatcher, action: AnyRef, next: (AnyRef) => ActionResult[Model]) = {
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
      'Filter - {
        val c = circuit
        val p = new ActionProcessor[Model] {
          override def process(dispatcher: Dispatcher, action: AnyRef, next: (AnyRef) => ActionResult[Model]) = {
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
      'LogState - {
        val c = circuit
        var log = "log"
        val p = new ActionProcessor[Model] {
          override def process(dispatcher: Dispatcher, action: AnyRef, next: (AnyRef) => ActionResult[Model]) = {
            next(action) match {
              case m: ModelUpdated[Model] =>
                log += m.newValue.s
                m
              case r => r
            }
          }
        }
        c.addProcessor(p)
        c.dispatch(SetS("Test"))
        assert(log == "logTest")
      }
      'Delay - {
        val c = circuit
        class AP extends ActionProcessor[Model] {
          val pending = mutable.Queue.empty[(AnyRef, Dispatcher)]

          override def process(dispatcher: Dispatcher, action: AnyRef, next: (AnyRef) => ActionResult[Model]) = {
            action match {
              case Delay(a) =>
                pending.enqueue((a, dispatcher))
                NoChange
              case _ => next(action)
            }
          }

          def run = {
            pending.foreach { case (action, dispatcher) => dispatcher(action) }
            pending.clear()
          }
        }
        val p = new AP
        c.addProcessor(p)
        c.dispatch(Delay(SetS("Test")))
        assert(c.model.s == "Testing")
        p.run
        assert(c.model.s == "Test")
      }
    }
  }
}
