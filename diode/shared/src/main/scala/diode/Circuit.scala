package diode

trait Dispatcher {
  def dispatch(action: AnyRef): Unit

  def apply(action: AnyRef) = dispatch(action)
}

trait ActionProcessor[M] {
  def process(dispatch: Dispatcher, action: AnyRef, next: (AnyRef) => ActionResult[M]): ActionResult[M]
}

trait Circuit[M <: AnyRef] extends Dispatcher {

  type Listener = () => Unit
  type HandlerFunction = PartialFunction[AnyRef, ActionResult[M]]

  protected var model: M
  protected def actionHandler: HandlerFunction

  private val modelRW = new RootModelRW[M](model)
  private var listeners = Set.empty[Listener]
  private var processors = List.empty[ActionProcessor[M]]
  private var processChain = buildProcessChain

  private def buildProcessChain = {
    // chain processing functions
    processors.reverse.foldLeft(process _)( (next, processor) =>
      (action: AnyRef) => processor.process(this, action, next)
    )
  }

  private val baseHandler: HandlerFunction = {
    case seq: Seq[_] =>
      // dispatch all actions in the sequence
      seq.asInstanceOf[Seq[AnyRef]].foreach(dispatchBase)
      ActionResult.NoChange
    case None =>
      // ignore
      ActionResult.NoChange
    case action =>
      logError(s"Action $action was not handled by and action handler")
      ActionResult.NoChange
  }

  def zoom[T](get: M => T): ModelR[T] = modelRW.zoom[T](get)

  def zoomRW[T](get: M => T)(set: (M, T) => M): ModelRW[M, T] = modelRW.zoomRW(get)(set)

  def subscribe(listener: Listener): () => Unit = {
    this.synchronized(listeners += listener)
    () => this.synchronized(listeners -= listener)
  }

  def addProcessor(processor: ActionProcessor[M]): Unit = {
    processors = processor :: processors
    processChain = buildProcessChain
  }

  def removeProcessor(processor: ActionProcessor[M]): Unit = {
    processors = processors.filterNot(_ == processor)
    processChain = buildProcessChain
  }

  def combineHandlers(handlers: ActionHandler[M, _]*): HandlerFunction = {
    handlers.foldLeft(PartialFunction.empty[AnyRef, ActionResult[M]])((a, b) => a orElse b.handle)
  }

  def logFatal(action: AnyRef, e: Throwable): Unit = throw e

  def logError(msg: String): Unit = ()

  private def update(newModel: M) = {
    if (newModel ne model) {
      model = newModel
    }
  }

  private def process(action: AnyRef): ActionResult[M] = {
    (actionHandler orElse baseHandler) (action)
  }

  /**
    * Dispatch the action, call change listeners when completed
    */
  def dispatch(action: AnyRef): Unit = {
    val oldModel = model
    dispatchBase(action)
    if (oldModel ne model) {
      listeners.foreach(f => f())
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
          if( effects.nonEmpty ) {
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
        logFatal(action, e)
    }
  }
}
