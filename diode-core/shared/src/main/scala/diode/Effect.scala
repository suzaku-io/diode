package diode

import diode.util.RunAfter

import scala.concurrent._
import scala.concurrent.duration.FiniteDuration
import scala.language.implicitConversions

trait Effect {
  /**
    * Runs the effect and dispatches the result of the effect.
    *
    * @param dispatch Function to dispatch the effect result with.
    * @return A future that completes when the effect completes.
    */
  def run(dispatch: Action => Unit): Future[Unit]

  /**
    * Combines two effects so that will be run in parallel.
    */
  def +(that: Effect): EffectSet

  /**
    * Combines another effect with this one, to be run after this effect.
    */
  def >>(that: Effect): EffectSeq

  /**
    * Combines another effect with this one, to be run before this effect.
    */
  def <<(that: Effect): EffectSeq

  /**
    * Returns the number of effects
    */
  def size: Int

  /**
    * Runs the effect function and returns its value (a Future[Action])
    */
  def toFuture: Future[List[Action]]

  /**
    * Delays the execution of this effect by duration `delay`
    */
  def after(delay: FiniteDuration)(implicit runner: RunAfter): Effect

  /**
    * Creates a new effect by applying a function to the successful result of
    * this effect. If this effect is completed with an exception then the new
    * effect will also contain this exception.
    */
  def map(g: Action => Action): Effect

  /**
    * Creates a new effect by applying a function to the successful result of
    * this effect, and returns the result of the function as the new future.
    * If this effect is completed with an exception then the new
    * effect will also contain this exception.
    */
  def flatMap(g: Action => Future[Action]): Effect

  def ec: ExecutionContext
}

abstract class EffectBase(val ec: ExecutionContext) extends Effect {
  self =>
  override def +(that: Effect) = new EffectSet(this, Set(that), ec)

  override def >>(that: Effect) = new EffectSeq(this, List(that), ec)

  override def <<(that: Effect) = new EffectSeq(that, List(this), ec)

  override def size = 1

  override def after(delay: FiniteDuration)(implicit runner: RunAfter): Effect = new DelayedEffect(runner, self, delay, ec)

}

class DelayedEffect[+A](runner: RunAfter, head: Effect, delay: FiniteDuration, ec: ExecutionContext) extends EffectBase(ec) {
  private def executeWith[B](f: Effect => Future[B]): Future[B] =
    runner.runAfter(delay)(()).flatMap(_ => f(head))(ec)

  override def run(dispatch: Action => Unit) =
    executeWith(_.run(dispatch))

  override def toFuture =
    executeWith(_.toFuture)

  override def map(g: Action => Action): Effect =
    new DelayedEffect(runner, head.map(g), delay, ec)

  override def flatMap(g: Action => Future[Action]): Effect =
    new DelayedEffect(runner, head.flatMap(g), delay, ec)
}


/**
  * Wraps a function to be executed later. Function must return a `Future[A]` and the returned
  * action is automatically dispatched when `run` is called.
  *
  * @param f The effect function, returning a `Future[A]`
  */
class EffectSingle[A <: Action](f: () => Future[A], ec: ExecutionContext) extends EffectBase(ec) {
  override def run(dispatch: Action => Unit) = f().map(dispatch)(ec)

  override def toFuture = f().map(x => List(x))(ec)

  override def map(g: Action => Action): Effect =
    new EffectSingle(() => f().map(g)(ec), ec)

  override def flatMap(g: Action => Future[Action]): Effect =
    new EffectSingle(() => f().flatMap(g)(ec), ec)

}

/**
  * Wraps multiple `Effects` to be executed later. Effects are executed in the order they appear and the
  * next effect is run only after the previous has completed. If an effect fails, the execution stops.
  *
  * @param head First effect to be run.
  * @param tail Rest of the effects.
  */
class EffectSeq(head: Effect, tail: Seq[Effect], ec: ExecutionContext) extends EffectBase(ec) {
  private def executeWith[A](f: Effect => Future[A]): Future[A] =
    tail.foldLeft(f(head)) { (prev, effect) => prev.flatMap(_ => f(effect))(ec) }

  override def run(dispatch: (Action) => Unit) =
    executeWith(_.run(dispatch))

  override def >>(that: Effect) =
    new EffectSeq(head, tail :+ that, ec)

  override def <<(that: Effect) =
    new EffectSeq(that, head +: tail, ec)

  override def size =
    head.size + tail.foldLeft(0)((acc, e) => acc + e.size)

  override def toFuture =
    executeWith(_.toFuture)

  override def map(g: Action => Action) =
    new EffectSeq(head.map(g), tail.map(_.map(g)), ec)

  override def flatMap(g: Action => Future[Action]) =
    new EffectSeq(head.flatMap(g), tail.map(_.flatMap(g)), ec)

}

/**
  * Wraps multiple `Effects` to be executed later. Effects are executed in parallel without any ordering.
  *
  * @param head First effect to be run.
  * @param tail Rest of the effects.
  */
class EffectSet(head: Effect, tail: Set[Effect], override implicit val ec: ExecutionContext) extends EffectBase(ec) {
  private def executeWith[A](f: Effect => Future[A]): Future[Set[A]] =
    Future.traverse(tail + head)(f(_))

  override def run(dispatch: (Action) => Unit) =
    executeWith(_.run(dispatch)).map(_ => ())

  override def +(that: Effect) =
    new EffectSet(head, tail + that, ec)

  override def size =
    head.size + tail.foldLeft(0)((acc, e) => acc + e.size)

  override def toFuture =
    executeWith(_.toFuture).map(_.flatten.toList)(ec)

  override def map(g: Action => Action) =
    new EffectSet(head.map(g), tail.map(_.map(g)), ec)

  override def flatMap(g: Action => Future[Action]) =
    new EffectSet(head.flatMap(g), tail.map(_.flatMap(g)), ec)

}

object Effect {
  type EffectF[A] = () => Future[A]

  def apply[A <: Action](f: => Future[A])(implicit ec: ExecutionContext): EffectSingle[A] =
    new EffectSingle(f _, ec)

  def apply(f: => Future[Action], tail: EffectF[Action]*)(implicit ec: ExecutionContext): EffectSet =
    new EffectSet(new EffectSingle(f _, ec), tail.map(f => new EffectSingle(f, ec)).toSet, ec)

  /**
    * Converts a lazy action value into an effect. Typically used in combination with other effects or
    * with `after` to delay execution.
    */
  def action[A <: Action](action: => A)(implicit ec: ExecutionContext): EffectSingle[A] =
    new EffectSingle(() => Future.successful(action), ec)

  implicit def f2effect[A <: Action](f: EffectF[A])(implicit ec: ExecutionContext): EffectSingle[A] = new EffectSingle(f, ec)
}
