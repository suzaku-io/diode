package example

import diode._

import scala.concurrent.ExecutionContext.Implicits.global

// Define the root of our application model
case class RootModel(animations: Map[Int, Animated], now: Double)

case class Point(x: Double, y: Double)

case class Animated(started: Double, animation: Animation, isRunning: Boolean = false, pausedAt: Double = 0) {
  def pause(time: Double)    = copy(isRunning = false, pausedAt = time)
  def continue(time: Double) = copy(started = time - pausedAt + started, isRunning = true)
  def updated(time: Double)  = copy(animation = animation.update((time - started) / 1000.0))
}

trait Animation {
  def update(delta: Double): Animation
  def color: String = "red"
  def position: Point
  def scale: Double = 1.0
}

// Define different animations
case class Circle(rpm: Double, position: Point = Point(1, 0)) extends Animation {
  override def update(delta: Double) = {
    val newPos = Point(math.cos(rpm * delta / 60), math.sin(rpm * delta / 60))
    copy(position = newPos)
  }
}

case class Spiral(rpm: Double, position: Point = Point(1, 0)) extends Animation {
  override def update(delta: Double) = {
    val radius = (math.cos(rpm * delta / 180) + 1) / 2
    val newPos = Point(math.cos(rpm * delta / 60) * radius, math.sin(rpm * delta / 60) * radius)
    copy(position = newPos)
  }
}

case class Flower(rpm: Double, position: Point = Point(1, 0), override val scale: Double = 1.0) extends Animation {
  override def update(delta: Double) = {
    val radius = (math.cos(rpm * delta / 10) + 1) / 2
    val newPos = Point(math.cos(rpm * delta / 60) * radius, math.sin(rpm * delta / 60) * radius)
    copy(position = newPos, scale = (math.cos(rpm * delta / 10) + 1.1) / 2.2)
  }
}

// Define actions
case object Reset extends Action

case class AddAnimation(animation: Animation) extends Action

case class UpdateAnimation(id: Int) extends RAFAction with Action

case class StartAnimation(id: Int, animation: Animation) extends RAFAction with Action

case class PauseAnimation(id: Int) extends RAFAction with Action

case class ContinueAnimation(id: Int) extends RAFAction with Action

case class DeleteAnimation(id: Int) extends RAFAction with Action

/**
  * AppCircuit provides the actual instance of the `RootModel` and all the action
  * handlers we need. Everything else comes from the `Circuit`
  */
object AppCircuit extends Circuit[RootModel] {
  // define initial value for the application model
  def initialModel = RootModel(Map(), System.currentTimeMillis())

  // zoom into the model, providing access only to the animations
  val animationHandler = new AnimationHandler(zoomTo(_.animations), zoom(_.now))

  val timestampHandler = new ActionHandler(zoomTo(_.now)) {
    override def handle = {
      case RAFTimeStamp(time) =>
        updated(time)
    }
  }

  val actionHandler = composeHandlers(animationHandler, timestampHandler)
}

class AnimationHandler[M](modelRW: ModelRW[M, Map[Int, Animated]], now: ModelRO[Double]) extends ActionHandler(modelRW) {
  def updateOne(id: Int, f: Animated => Animated) = {
    value.get(id).fold(value)(a => value.updated(id, f(a)))
  }

  override def handle = {
    case Reset =>
      updated(Map.empty[Int, Animated])

    case UpdateAnimation(id) =>
      // request next update if animation is still running
      value.get(id).filter(_.isRunning) match {
        case Some(_) =>
          updated(updateOne(id, _.updated(now())), Effect.action(UpdateAnimation(id)))
        case None =>
          updated(updateOne(id, _.updated(now())))
      }

    case AddAnimation(animation) =>
      val id = value.keys.foldLeft(0)((a, id) => a max id) + 1
      // request update to start animation
      updated(value + (id -> Animated(now(), animation, true)), Effect.action(UpdateAnimation(id)))

    case DeleteAnimation(id) =>
      updated(value.filterNot(_._1 == id))

    case PauseAnimation(id) =>
      updated(updateOne(id, _.pause(now())))

    case ContinueAnimation(id) =>
      // request update to start animation
      updated(updateOne(id, _.continue(now())), Effect.action(UpdateAnimation(id)))
  }
}
