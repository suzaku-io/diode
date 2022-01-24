package diode

import scala.annotation.implicitNotFound
import scala.collection.immutable.Queue

/**
  * The `ActionType` type class is used to verify that only valid actions are dispatched. An implicit instance of
  * `ActionType[A]` must be in scope when calling dispatch methods or creating effects that return actions.
  *
  * `ActionType` is contravariant, which means it's enough to have an instance of `ActionType` for a common supertype to be
  * able to dispatch actions of its subtypes. For example providing an instance of `ActionType[Action]` allows dispatching
  * any class that is a subtype of `Action`.
  *
  * @tparam A
  *   Action type
  */
@implicitNotFound(
  msg =
    "Cannot find an ActionType type class for action of type ${A}. Make sure to provide an implicit ActionType for dispatched actions."
)
trait ActionType[-A]

trait Dispatcher {
  def dispatch[A: ActionType](action: A): Unit

  def apply[A: ActionType](action: A) = dispatch(action)
}

/**
  * Base trait for actions. Use this as a basis for your action class hierarchy to get an automatic type class instance of
  * `ActionType[Action]`. Note that this trait is just a helper, you don't need to use it for your actions as you can always
  * define your own `ActionType` instances for your action types.
  */
trait Action

object Action {
  implicit object aType extends ActionType[Action]
}

/**
  * A batch of actions. These actions are dispatched in a batch, without calling listeners in-between the dispatches.
  *
  * @param actions
  *   Sequence of actions to dispatch
  */
class ActionBatch private (val actions: Seq[Any]) extends Action {
  def :+[A: ActionType](action: A): ActionBatch =
    new ActionBatch(actions :+ action)

  def +:[A: ActionType](action: A) =
    new ActionBatch(action +: actions)

  def ++(batch: ActionBatch) =
    new ActionBatch(actions ++ batch.actions)
}

object ActionBatch {
  def apply[A: ActionType](actions: A*): ActionBatch = new ActionBatch(actions)
}

/**
  * Use `NoAction` when you need to dispatch an action that does nothing
  */
case object NoAction extends Action

trait ActionProcessor[M <: AnyRef] {
  def process(dispatch: Dispatcher, action: Any, next: Any => ActionResult[M], currentModel: M): ActionResult[M]
}

sealed trait ActionResult[+M] {
  def newModelOpt: Option[M]    = None
  def effectOpt: Option[Effect] = None
}

sealed trait ModelUpdated[+M] extends ActionResult[M] {
  def newModel: M
  override def newModelOpt: Option[M] = Some(newModel)
}

sealed trait HasEffect[+M] extends ActionResult[M] {
  def effect: Effect
  override def effectOpt: Option[Effect] = Some(effect)
}

sealed trait UpdateSilent

object ActionResult {

  case object NoChange extends ActionResult[Nothing]

  final case class ModelUpdate[M](newModel: M) extends ModelUpdated[M]

  final case class ModelUpdateSilent[M](newModel: M) extends ModelUpdated[M] with UpdateSilent

  final case class EffectOnly(effect: Effect) extends ActionResult[Nothing] with HasEffect[Nothing]

  final case class ModelUpdateEffect[M](newModel: M, effect: Effect) extends ModelUpdated[M] with HasEffect[M]

  final case class ModelUpdateSilentEffect[M](newModel: M, effect: Effect)
      extends ModelUpdated[M]
      with HasEffect[M]
      with UpdateSilent

  def apply[M](model: Option[M], effect: Option[Effect]): ActionResult[M] = (model, effect) match {
    case (Some(m), Some(e)) => ModelUpdateEffect(m, e)
    case (Some(m), None)    => ModelUpdate(m)
    case (None, Some(e))    => EffectOnly(e)
    case _                  => NoChange
  }
}

trait Circuit[M <: AnyRef] extends Dispatcher with ZoomTo[M, M] {

  type HandlerFunction = (M, Any) => Option[ActionResult[M]]

  private case class Subscription[T](listener: ModelRO[T] => Unit, cursor: ModelR[M, T], lastValue: T) {
    def changed: Option[Subscription[T]] = {
      if (cursor === lastValue)
        None
      else
        Some(copy(lastValue = cursor.eval(model)))
    }

    def call(): Unit = listener(cursor)
  }

  private[diode] var model: M = initialModel

  /**
    * Provides the initial value for the model
    */
  protected def initialModel: M

  /**
    * Handles all dispatched actions
    *
    * @return
    */
  protected def actionHandler: HandlerFunction

  private val modelRW       = new RootModelRW[M](model)
  private var isDispatching = false
  private var dispatchQueue = Queue.empty[Any]
  private var listenerId    = 0
  private var listeners     = Map.empty[Int, Subscription[_]]
  private var processors    = List.empty[ActionProcessor[M]]
  private var processChain  = buildProcessChain

  private def buildProcessChain = {
    // chain processing functions
    processors.reverse.foldLeft(process _)((next, processor) =>
      (action: Any) => processor.process(this, action, next, model)
    )
  }

  /**
    * Zoom into the model using the `get` function
    *
    * @param get
    *   Function that returns the part of the model you are interested in
    * @return
    *   A `ModelR[T]` giving you read-only access to part of the model
    */
  def zoom[T](get: M => T)(implicit feq: FastEq[_ >: T]): ModelR[M, T] =
    modelRW.zoom[T](get)

  def zoomMap[F[_], A, B](fa: M => F[A])(f: A => B)(implicit monad: Monad[F], feq: FastEq[_ >: B]): ModelR[M, F[B]] =
    modelRW.zoomMap(fa)(f)

  def zoomFlatMap[F[_], A, B](fa: M => F[A])(f: A => F[B])(implicit monad: Monad[F], feq: FastEq[_ >: B]): ModelR[M, F[B]] =
    modelRW.zoomFlatMap(fa)(f)

  /**
    * Zoom into the model using `get` and `set` functions
    *
    * @param get
    *   Function that returns the part of the model you are interested in
    * @param set
    *   Function that updates the part of the model you are interested in
    * @return
    *   A `ModelRW[T]` giving you read/update access to part of the model
    */
  def zoomRW[T](get: M => T)(set: (M, T) => M)(implicit feq: FastEq[_ >: T]): ModelRW[M, T] = modelRW.zoomRW(get)(set)

  def zoomMapRW[F[_], A, B](fa: M => F[A])(f: A => B)(
      set: (M, F[B]) => M
  )(implicit monad: Monad[F], feq: FastEq[_ >: B]): ModelRW[M, F[B]] =
    modelRW.zoomMapRW(fa)(f)(set)

  def zoomFlatMapRW[F[_], A, B](fa: M => F[A])(f: A => F[B])(
      set: (M, F[B]) => M
  )(implicit monad: Monad[F], feq: FastEq[_ >: B]): ModelRW[M, F[B]] =
    modelRW.zoomFlatMapRW(fa)(f)(set)

  /**
    * Subscribes to listen to changes in the model. By providing a `cursor` you can limit what part of the model must change
    * for your listener to be called. If omitted, all changes result in a call.
    *
    * @param cursor
    *   Model reader returning the part of the model you are interested in.
    * @param listener
    *   Function to be called when model is updated. The listener function gets the model reader as a parameter.
    * @return
    *   A function to unsubscribe your listener
    */
  def subscribe[T](cursor: ModelR[M, T])(listener: ModelRO[T] => Unit): () => Unit = {
    this.synchronized {
      listenerId += 1
      val id = listenerId
      listeners += id -> Subscription(listener, cursor, cursor.eval(model))
      () => this.synchronized(listeners -= id)
    }
  }

  /**
    * Adds a new `ActionProcessor[M]` to the action processing chain. The processor is called for every dispatched action.
    *
    * @param processor
    */
  def addProcessor(processor: ActionProcessor[M]): Unit = {
    this.synchronized {
      processors = processor :: processors
      processChain = buildProcessChain
    }
  }

  /**
    * Removes a previously added `ActionProcessor[M]` from the action processing chain.
    *
    * @param processor
    */
  def removeProcessor(processor: ActionProcessor[M]): Unit = {
    this.synchronized {
      processors = processors.filterNot(_ == processor)
      processChain = buildProcessChain
    }
  }

  /**
    * Handle a fatal error. Override this function to do something with exceptions that occur while dispatching actions.
    *
    * @param action
    *   Action that caused the exception
    * @param e
    *   Exception that was thrown
    */
  def handleFatal(action: Any, e: Throwable): Unit = throw e

  /**
    * Handle a non-fatal error, such as dispatching an action with no action handler.
    *
    * @param msg
    *   Error message
    */
  def handleError(msg: String): Unit = throw new Exception(s"handleError called with: $msg")

  /**
    * @param action
    *   the action that caused the effects
    * @param error
    *   the Exception that was encountered while processing the effects
    */
  def handleEffectProcessingError[A](action: A, error: Throwable): Unit = {
    handleError(s"Error in processing effects for action $action: $error")
  }

  /**
    * Updates the model if it has changed (reference equality check)
    */
  private def update(newModel: M) = {
    if (newModel ne model) {
      model = newModel
    }
  }

  /**
    * The final action processor that does actual action handling.
    *
    * @param action
    *   Action to be handled
    * @return
    */
  private def process(action: Any): ActionResult[M] =
    action match {
      case b: ActionBatch =>
        // dispatch all actions in the sequence using internal dispatchBase to prevent
        // additional calls to subscribed listeners
        b.actions.foreach(a => dispatchBase(a))
        ActionResult.NoChange
      case NoAction =>
        // ignore
        ActionResult.NoChange
      case _ =>
        actionHandler(model, action).getOrElse {
          handleError(s"Action $action was not handled by any action handler")
          ActionResult.NoChange
        }
    }

  /**
    * Composes multiple handlers into a single handler. Processing stops as soon as a handler is able to handle the action.
    * If none of them handle the action, `None` is returned
    */
  def composeHandlers(handlers: HandlerFunction*): HandlerFunction =
    (model, action) => {
      handlers.foldLeft(Option.empty[ActionResult[M]]) { (a, b) =>
        a.orElse(b(model, action))
      }
    }

  @deprecated("Use composeHandlers or foldHandlers instead", "0.5.1")
  def combineHandlers(handlers: HandlerFunction*): HandlerFunction = composeHandlers(handlers: _*)

  /**
    * Folds multiple handlers into a single function so that each handler is called in turn and an updated model is passed on
    * to the next handler. Returned `ActionResult` contains the final model and combined effects.
    */
  def foldHandlers(handlers: HandlerFunction*): HandlerFunction =
    (initialModel, action) => {
      handlers
        .foldLeft((initialModel, Option.empty[ActionResult[M]])) {
          case ((currentModel, currentResult), handler) =>
            handler(currentModel, action) match {
              case None =>
                (currentModel, currentResult)
              case Some(result) =>
                val (nextModel, nextResult) = currentResult match {
                  case Some(cr) =>
                    val newEffect = (cr.effectOpt, result.effectOpt) match {
                      case (Some(e1), Some(e2)) => Some(e1 + e2)
                      case (Some(e1), None)     => Some(e1)
                      case (None, Some(e2))     => Some(e2)
                      case (None, None)         => None
                    }
                    val newModel = result.newModelOpt.orElse(cr.newModelOpt)
                    (newModel.getOrElse(currentModel), ActionResult(newModel, newEffect))
                  case None =>
                    (result.newModelOpt.getOrElse(currentModel), result)
                }
                (nextModel, Some(nextResult))
            }
        }
        ._2
    }

  /**
    * Dispatch the action, call change listeners when completed
    *
    * @param action
    *   Action to dispatch
    */
  def dispatch[A: ActionType](action: A): Unit = {
    this.synchronized {
      if (!isDispatching) {
        try {
          isDispatching = true
          val oldModel = model
          val silent   = dispatchBase(action)
          if (oldModel ne model) {
            // walk through all listeners and update subscriptions when model has changed
            val updated = listeners.foldLeft(listeners) {
              case (l, (key, sub)) =>
                if (listeners.isDefinedAt(key)) {
                  // Listener still exists
                  sub.changed match {
                    case Some(newSub) =>
                      // value at the cursor has changed, call listener and update subscription
                      if (!silent) sub.call()
                      l.updated(key, newSub)
                    case None => l // nothing interesting happened
                  }
                } else {
                  l // Listener was removed since we started
                }
            }

            // Listeners may have changed during processing (subscribe or unsubscribe)
            // so only update the listeners that are still there, and leave any new listeners that may be there now.
            listeners = updated.foldLeft(listeners) {
              case (l, (key, sub)) =>
                if (l.isDefinedAt(key))
                  l.updated(key, sub) // Listener still exists for this key
                else
                  l // Listener was removed for this key, skip it
            }
          }
        } catch {
          case e: Throwable =>
            handleFatal(action, e)
        } finally {
          isDispatching = false
        }
        // if there is an item in the queue, dispatch it
        dispatchQueue.dequeueOption foreach {
          case (nextAction, queue) =>
            dispatchQueue = queue
            dispatch(nextAction)(null)
        }
      } else {
        // add to the queue
        dispatchQueue = dispatchQueue.enqueue(action)
      }
    }
  }

  /**
    * Perform actual dispatching, without calling change listeners
    */
  protected def dispatchBase[A](action: A): Boolean = {
    import AnyAction._
    try {
      processChain(action) match {
        case ActionResult.NoChange =>
          // no-op
          false
        case ActionResult.ModelUpdate(newModel) =>
          update(newModel)
          false
        case ActionResult.ModelUpdateSilent(newModel) =>
          update(newModel)
          true
        case ActionResult.EffectOnly(effects) =>
          // run effects
          effects
            .run(a => dispatch(a))
            .recover {
              case e: Throwable => handleEffectProcessingError(action, e)
            }(effects.ec)
          true
        case ActionResult.ModelUpdateEffect(newModel, effects) =>
          update(newModel)
          // run effects
          effects
            .run(a => dispatch(a))
            .recover {
              case e: Throwable => handleEffectProcessingError(action, e)
            }(effects.ec)
          false
        case ActionResult.ModelUpdateSilentEffect(newModel, effects) =>
          update(newModel)
          // run effects
          effects
            .run(a => dispatch(a))
            .recover {
              case e: Throwable => handleEffectProcessingError(action, e)
            }(effects.ec)
          true
      }
    } catch {
      case e: Throwable =>
        handleFatal(action, e)
        true
    }
  }
}
