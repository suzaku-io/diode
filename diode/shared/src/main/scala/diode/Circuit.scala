package diode

import scala.concurrent._

trait Dispatcher {
  def dispatch(action: AnyRef): Unit

  def apply(action: AnyRef) = dispatch(action)
}

trait Circuit[M <: AnyRef] extends Dispatcher {

  type Listener = () => Unit
  type HandlerFunction = PartialFunction[AnyRef, ActionResult[M]]

  protected var model: M

  protected def actionHandler: HandlerFunction

  private val baseHandler: HandlerFunction = {
    case action =>
      logError(s"Action $action was not handled by and action handler")
      ActionResult.ModelUpdate(model) // always return current model
  }

  private val modelRW = new RootModelRW[M](model)

  private var listeners = Set.empty[Listener]

  def zoom[T](get: M => T): ModelR[T] = modelRW.zoom[T](get)

  def zoomRW[T](get: M => T)(set: (M, T) => M): ModelRW[M, T] = modelRW.zoomRW(get)(set)

  def subscribe(listener: Listener): () => Unit = {
    this.synchronized(listeners += listener)
    () => this.synchronized(listeners -= listener)
  }

  def combineHandlers(handlers: ActionHandler[M, _]*): HandlerFunction = {
    handlers.foldLeft(PartialFunction.empty[AnyRef, ActionResult[M]])((a, b) => a orElse b.handle)
  }

  def logFatal(e: Throwable): Unit = throw e

  def logError(msg: String): Unit = ()

  private def update(newModel: M) = {
    if (newModel ne model) {
      model = newModel
      listeners.foreach(f => f())
    }
  }

  def dispatch(action: AnyRef): Unit = {
    this.synchronized {
      try {
        (actionHandler orElse baseHandler) (action) match {
          case ActionResult.ModelUpdate(newModel) =>
            update(newModel)
          case ActionResult.ModelUpdateEffect(newModel, effects, ec) =>
            implicit val exCon = ec
            update(newModel)
            // run effects serially
            effects.foldLeft(Future.successful(())) { (prev, effect) =>
              prev.flatMap(_ => effect().map(dispatch))
            }
          case ActionResult.ModelUpdateEffectPar(newModel, effects, ec) =>
            implicit val exCon = ec
            update(newModel)
            // run effects in parallel
            effects.foreach(effect => effect().map(dispatch))
        }
      } catch {
        case e: Throwable =>
          logFatal(e)
      }
    }
  }
}
