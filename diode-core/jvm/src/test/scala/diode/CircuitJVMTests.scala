package diode

import utest._

import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object CircuitJVMTests extends TestSuite {

  implicit object AnyAction extends ActionType[Any]

  case class Model(list: Vector[Int])

  // actions
  case class Append(i: Int)

  case class Prepend(i: Int)

  case class Filter(f: Int => Boolean)

  case class RunEffects(effects: Seq[Effect], parallel: Boolean = false)

  class TestCircuit extends Circuit[Model] {
    import diode.ActionResult._
    override def initialModel = Model(Vector.empty)
    override protected def actionHandler: HandlerFunction =
      (model, action) =>
        ({
          case Append(i) =>
            ModelUpdate(Model(model.list :+ i))
          case Prepend(i) =>
            ModelUpdate(Model(i +: model.list))
          case Filter(f) =>
            ModelUpdate(Model(model.list.filter(f)))
          case RunEffects(effects, parallel) =>
            if (parallel)
              ModelUpdateEffect(model, effects.reduce(_ + _))
            else
              ModelUpdateEffect(model, effects.reduce(_ >> _))
        }: PartialFunction[Any, ActionResult[Model]]).lift.apply(action)
  }

  def tests = TestSuite {
    def circuit = new TestCircuit

    "ParallelActions" - {
      val c = circuit

      // run 1000 concurrent actions
      var futures = List.empty[Future[Unit]]
      for (i <- 0 until 1000) {
        futures ::= Future(c.dispatch(Append(i)))
        futures ::= Future(c.dispatch(Prepend(i)))
      }
      // wait for futures to complete
      Await.ready(Future.sequence(futures), 1000.millis)
      assert(c.model.list.size == 2000)

      futures = List.empty[Future[Unit]]
      for (i <- 0 until 1000) {
        futures ::= Future {
          c.dispatch(Append(i))
          c.dispatch(Filter(_ == i))
        }
      }
      // wait for futures to complete
      Await.ready(Future.sequence(futures), 1000.millis)
      assert(c.model.list.size < 10)
    }
    "SerialEffects" - {
      val c = circuit

      // run 1000 serial effects actions
      val effects = for (i <- 0 until 1000) yield {
        Effect(Future(Append(i)))
      }
      c.dispatch(RunEffects(effects))
      // wait for futures to complete
      Thread.sleep(300)
      assert(c.model.list.size == 1000)
      assert(c.model.list == Vector.range(0, 1000))
    }
    "ParallelEffects" - {
      val c = circuit

      // run 1000 serial effects actions
      val effects = for (i <- 0 until 1000) yield {
        Effect(Future(Append(i)))
      }
      c.dispatch(RunEffects(effects, true))
      // wait for futures to complete
      Thread.sleep(300)
      assert(c.model.list.size == 1000)
      assert(c.model.list != Vector.range(0, 1000))
    }
    "SequenceActions" - {
      val c       = circuit
      val actions = for (i <- 0 until 1000) yield Append(i)
      c.dispatch(ActionBatch(actions: _*))
      assert(c.model.list.size == 1000)
      assert(c.model.list == Vector.range(0, 1000))
    }
    "SequenceActionEffects" - {
      val c       = circuit
      val actions = for (i <- 0 until 1000) yield RunEffects(Seq(() => Future(Append(i))))
      c.dispatch(ActionBatch(actions: _*))
      // wait for futures to complete
      Thread.sleep(300)
      assert(c.model.list.size == 1000)
      assert(c.model.list != Vector.range(0, 1000))
    }
    "ParallelSubscribe" - {
      val c = circuit
      class Listen {
        var called                                       = false
        def listener(cursor: ModelRO[Vector[Int]]): Unit = called = !called
      }
      val listeners = for (i <- 0 until 1000) yield new Listen
      // add in parallel
      val futures = listeners.map(l => Future(c.subscribe(c.zoom(_.list))(l.listener)))
      // wait for futures to complete
      Await.ready(Future.sequence(futures), 1000.millis)
      c.dispatch(Append(0))
      listeners.foreach(l => assert(l.called))
      // call unsubscribe functions in future for half
      val nextFutures = futures.take(500).map(l => l.flatMap(f => Future(f())))
      // wait for futures to complete
      Await.ready(Future.sequence(nextFutures), 1000.millis)
      c.dispatch(Append(0))
      assert(listeners.count(l => l.called) == 500)
    }
  }
}
