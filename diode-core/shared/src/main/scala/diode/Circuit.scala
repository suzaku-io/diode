package diode

import scala.language.higherKinds

trait Dispatcher {
  def dispatch(action: AnyRef): Unit

  def apply(action: AnyRef) = dispatch(action)
}

trait ActionProcessor[M <: AnyRef] {
  def process(dispatch: Dispatcher, action: AnyRef, next: (AnyRef) => ActionResult[M], currentModel: M): ActionResult[M]
}

trait Circuit[M <: AnyRef] extends Dispatcher {

  type HandlerFunction = PartialFunction[AnyRef, ActionResult[M]]

  private case class Subscription[T](listener: ModelR[M, T] => Unit, cursor: ModelR[M, T], lastValue: T) {
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
    * @return
    */
  protected def actionHandler: HandlerFunction

  private val modelRW = new RootModelRW[M](model)
  private var listenerId = 0
  private var listeners = Map.empty[Int, Subscription[_]]
  private var processors = List.empty[ActionProcessor[M]]
  private var processChain = buildProcessChain

  private def buildProcessChain = {
    // chain processing functions
    processors.reverse.foldLeft(process _)((next, processor) =>
      (action: AnyRef) => processor.process(this, action, next, model)
    )
  }

  private val baseHandler: HandlerFunction = {
    case seq: Seq[_] =>
      // dispatch all actions in the sequence using internal dispatchBase to prevent
      // additional calls to subscribed listeners
      seq.asInstanceOf[Seq[AnyRef]].foreach(dispatchBase)
      ActionResult.NoChange
    case None =>
      // ignore
      ActionResult.NoChange
    case action =>
      handleError(s"Action $action was not handled by any action handler")
      ActionResult.NoChange
  }

  /**
    * Zoom into the model using the `get` function
    *
    * @param get Function that returns the part of the model you are interested in
    * @return A `ModelR[T]` giving you read-only access to part of the model
    */
  def zoom[T](get: M => T)(implicit feq: FastEq[_ >: T]): ModelR[M, T] =
    modelRW.zoom[T](get)

  def zoomMap[F[_], A, B](fa: M => F[A])(f: A => B)
    (implicit monad: Monad[F], feq: FastEq[_ >: B]): ModelR[M, F[B]] =
    modelRW.zoomMap(fa)(f)

  def zoomFlatMap[F[_], A, B](fa: M => F[A])(f: A => F[B])
    (implicit monad: Monad[F], feq: FastEq[_ >: B]): ModelR[M, F[B]] =
    modelRW.zoomFlatMap(fa)(f)

  /**
    * Zoom into the model using `get` and `set` functions
    *
    * @param get Function that returns the part of the model you are interested in
    * @param set Function that updates the part of the model you are interested in
    * @return A `ModelRW[T]` giving you read/update access to part of the model
    */
  def zoomRW[T](get: M => T)(set: (M, T) => M)(implicit feq: FastEq[_ >: T]): ModelRW[M, T] = modelRW.zoomRW(get)(set)

  def zoomMapRW[F[_], A, B](fa: M => F[A])(f: A => B)(set: (M, F[B]) => M)
    (implicit monad: Monad[F], feq: FastEq[_ >: B]): ModelRW[M, F[B]] =
    modelRW.zoomMapRW(fa)(f)(set)

  def zoomFlatMapRW[F[_], A, B](fa: M => F[A])(f: A => F[B])(set: (M, F[B]) => M)
    (implicit monad: Monad[F], feq: FastEq[_ >: B]): ModelRW[M, F[B]] =
    modelRW.zoomFlatMapRW(fa)(f)(set)

  /**
    * Subscribes to listen to changes in the model. By providing a `cursor` you can limit
    * what part of the model must change for your listener to be called. If omitted, all changes
    * result in a call.
    *
    * @param cursor   Model reader returning the part of the model you are interested in.
    * @param listener Function to be called when model is updated. The listener function gets
    *                 the model reader as a parameter.
    * @return A function to unsubscribe your listener
    */
  def subscribe[T](cursor: ModelR[M, T])(listener: ModelR[M, T] => Unit): () => Unit = {
    this.synchronized {
      listenerId += 1
      val id = listenerId
      listeners += id -> Subscription(listener, cursor, cursor.eval(model))
      () => this.synchronized(listeners -= id)
    }
  }

  /**
    * Adds a new `ActionProcessor[M]` to the action processing chain. The processor is called for
    * every dispatched action.
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
    * Combines multiple `ActionHandler`s into a single partial function
    *
    * @param handlers
    * @return
    */
  def combineHandlers(handlers: ActionHandler[M, _]*): HandlerFunction = {
    handlers.foldLeft(PartialFunction.empty[AnyRef, ActionResult[M]])((a, b) => a orElse b.handle)
  }

  /**
    * Handle a fatal error. Override this function to do something with exceptions that
    * occur while dispatching actions.
    *
    * @param action Action that caused the exception
    * @param e      Exception that was thrown
    */
  def handleFatal(action: AnyRef, e: Throwable): Unit = throw e

  /**
    * Handle a non-fatal error, such as dispatching an action with no action handler.
    *
    * @param msg Error message
    */
  def handleError(msg: String): Unit = ()

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
    * @param action Action to be handled
    * @return
    */
  private def process(action: AnyRef): ActionResult[M] = {
    (actionHandler orElse baseHandler) (action)
  }

  /**
    * Dispatch the action, call change listeners when completed
    *
    * @param action Action to dispatch
    */
  def dispatch(action: AnyRef): Unit = {
    this.synchronized {
      val oldModel = model
      dispatchBase(action)
      if (oldModel ne model) {
        // walk through all listeners and update subscriptions when model has changed
        listeners = listeners.foldLeft(listeners) { case (l, (key, sub)) =>
          sub.changed match {
            case Some(newSub) =>
              // value at the cursor has changed, call listener and update subscription
              sub.call()
              l.updated(key, newSub)
            case None =>
              // nothing interesting happened
              l
          }
        }
      }
    }
  }

  /**
    * Perform actual dispatching, without calling change listeners
    */
  protected def dispatchBase(action: AnyRef): Unit = {
    try {
      processChain(action) match {
        case ActionResult.NoChange =>
        // no-op
        case ActionResult.ModelUpdate(newModel) =>
          update(newModel)
        case ActionResult.EffectOnly(effects) =>
          // run effects
          effects.run(dispatch).recover {
            case e: Throwable =>
              handleError(s"Error in processing effects for action $action: $e")
          }(effects.ec)
        case ActionResult.ModelUpdateEffect(newModel, effects) =>
          update(newModel)
          // run effects
          effects.run(dispatch).recover {
            case e: Throwable =>
              handleError(s"Error in processing effects for action $action: $e")
          }(effects.ec)
      }
    } catch {
      case e: Throwable =>
        handleFatal(action, e)
    }
  }
}
