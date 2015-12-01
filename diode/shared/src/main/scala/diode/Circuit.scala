package diode

trait Dispatcher {
  def dispatch(action: AnyRef): Unit

  def apply(action: AnyRef) = dispatch(action)
}

trait ActionProcessor {
  def process[M](dispatch: Dispatcher, action: AnyRef, next: (AnyRef) => ActionResult[M]): ActionResult[M]
}

trait Circuit[M <: AnyRef] extends Dispatcher {

  type Listener = () => Unit
  type HandlerFunction = PartialFunction[AnyRef, ActionResult[M]]
  type Cursor = (M) => AnyRef

  private case class Subscription(listener: Listener, cursor: Cursor, lastValue: AnyRef)

  protected var model: M
  protected def actionHandler: HandlerFunction

  private val modelRW = new RootModelRW[M](model)
  private var listenerId = 0
  private var listeners = Map.empty[Int, Subscription]
  private var processors = List.empty[ActionProcessor]
  private var processChain = buildProcessChain

  private def buildProcessChain = {
    // chain processing functions
    processors.reverse.foldLeft(process _)((next, processor) =>
      (action: AnyRef) => processor.process(this, action, next)
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
    * @tparam T
    * @return A `ModelR[T]` giving you read-only access to part of the model
    */
  def zoom[T](get: M => T): ModelR[T] = modelRW.zoom[T](get)

  /**
    * Zoom into the model using `get` and `set` functions
    *
    * @param get Function that returns the part of the model you are interested in
    * @param set Function that updates the part of the model you are interested in
    * @tparam T
    * @return A `ModelRW[T]` giving you read/update access to part of the model
    */
  def zoomRW[T](get: M => T)(set: (M, T) => M): ModelRW[M, T] = modelRW.zoomRW(get)(set)

  /**
    * Subscribes to listen to changes in the model. By providing a `cursor` you can limit
    * what part of the model must change for your listener to be called. If omitted, all changes
    * result in a call.
    *
    * @param listener Function to be called when model is updated
    * @param cursor Cursor function returning the part of the model you are interested in. By
    *               default this is the root model, which means your listener is called on any
    *               change in the model.
    * @return A function to unsubscribe your listener
    */
  def subscribe(listener: Listener, cursor: Cursor = m => m): () => Unit = {
    this.synchronized {
      listenerId += 1
      val id = listenerId
      listeners += id -> Subscription(listener, cursor, cursor(model))
      () => this.synchronized(listeners -= id)
    }
  }

  /**
    * Adds a new `ActionProcessor` to the action processing chain. The processor is called for
    * every dispatched action.
    *
    * @param processor
    */
  def addProcessor(processor: ActionProcessor): Unit = {
    this.synchronized {
      processors = processor :: processors
      processChain = buildProcessChain
    }
  }

  /**
    * Removes a previously added `ActionProcessor` from the action processing chain.
    *
    * @param processor
    */
  def removeProcessor(processor: ActionProcessor): Unit = {
    this.synchronized {
      processors = processors.filterNot(_ == processor)
      processChain = buildProcessChain
    }
  }

  /**
    * Combines multiple `ActionHandler`s into a single partial function
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
    * @param e Exception that was thrown
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
    * @param action Action to be handled
    * @return
    */
  private def process(action: AnyRef): ActionResult[M] = {
    (actionHandler orElse baseHandler) (action)
  }

  /**
    * Dispatch the action, call change listeners when completed
    * @param action Action to dispatch
    */
  def dispatch(action: AnyRef): Unit = {
    this.synchronized {
      val oldModel = model
      dispatchBase(action)
      if (oldModel ne model) {
        // walk through all listeners and update subscriptions when model has changed
        listeners = listeners.foldLeft(listeners) { case (l, (key, sub)) =>
          val curValue = sub.cursor(model)
          if (curValue ne sub.lastValue) {
            // value at the cursor has changed, call listener and update subscription
            sub.listener()
            l.updated(key, sub.copy(lastValue = curValue))
          } else {
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
        case ActionResult.ModelUpdateEffect(newModel, effects, ec) =>
          implicit val exCon = ec
          update(newModel)
          // run effects serially
          if (effects.nonEmpty) {
            effects.tail.foldLeft(effects.head.apply().map(dispatch)) { (prev, effect) =>
              prev.flatMap(_ => effect().map(dispatch))
            }
          }
        case ActionResult.ModelUpdateEffectPar(newModel, effects, ec) =>
          implicit val exCon = ec
          update(newModel)
          // run effects in parallel
          effects.foreach(effect => effect().map(dispatch))
      }
    } catch {
      case e: Throwable =>
        handleFatal(action, e)
    }
  }
}
