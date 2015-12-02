package diode

import diode.util.RunAfter

import scala.concurrent._
import scala.concurrent.duration.FiniteDuration
import scala.language.implicitConversions

trait Effects {
  /**
    * Runs the effect and dispatches the result of the effect.
    *
    * @param dispatch Function to dispatch the effect result with.
    * @return A future that completes when the effect completes.
    */
  def run(dispatch: AnyRef => Unit): Future[Any]

  /**
    * Combines two effects so that will be run in parallel.
    */
  def +(that: Effects): EffectSet

  /**
    * Combines another effect with this one, to be run after this effect.
    */
  def >>(that: Effects): EffectSeq

  /**
    * Combines another effect with this one, to be run before this effect.
    */
  def <<(that: Effects): EffectSeq

  /**
    * Returns the number of effects
    *
    * @return
    */
  def size: Int = 1

  /**
    * Runs the effect function and returns its value (a Future[AnyRef])
    *
    * @return
    */
  def toFuture: Future[AnyRef]

  /**
    * Delays the execution of this effect by duration `delay`
    */
  def after(delay: FiniteDuration)(implicit runner: RunAfter): Effects

  /**
    * Creates a new effect by applying a function to the successful result of
    * this effect. If this effect is completed with an exception then the new
    * effect will also contain this exception.
    */
  def map(g: AnyRef => AnyRef): Effects

  /**
    * Creates a new effect by applying a function to the successful result of
    * this effect, and returns the result of the function as the new future.
    * If this effect is completed with an exception then the new
    * effect will also contain this exception.
    */
  def flatMap(g: AnyRef => Future[AnyRef]): Effects

  def ec: ExecutionContext
}

abstract class EffectBase(val ec: ExecutionContext) extends Effects { self =>
  override def +(that: Effects): EffectSet = new EffectSet(this, Set(that), ec)

  override def >>(that: Effects): EffectSeq = new EffectSeq(this, List(that), ec)

  override def <<(that: Effects): EffectSeq = new EffectSeq(that, List(this), ec)

  override def after(delay: FiniteDuration)(implicit runner: RunAfter): Effects = new EffectBase(ec) {
    override def run(dispatch: (AnyRef) => Unit): Future[Any] =
      runner.runAfter(delay)(()).flatMap(_ => self.run(dispatch))(ec)

    override def toFuture =
      runner.runAfter(delay)(()).flatMap(_ => self.toFuture)(ec)
  }

  override def map(g: AnyRef => AnyRef): Effects =
    new Effect(() => toFuture.map(g)(ec), ec)

  override def flatMap(g: AnyRef => Future[AnyRef]): Effects =
    new Effect(() => toFuture.flatMap(g)(ec), ec)
}

/**
  * Wraps a function to be executed later. Function must return a `Future[A]` and the returned
  * action is automatically dispatched when `run` is called.
  *
  * @param f The effect function, returning a `Future[A]`
  */
class Effect[A <: AnyRef](f: () => Future[A], override implicit val ec: ExecutionContext) extends EffectBase(ec) {
  override def run(dispatch: AnyRef => Unit) = f().map(dispatch)

  override def toFuture = f()
}

/**
  * Wraps multiple `Effects` to be executed later. Effects are executed in the order they appear and the
  * next effect is run only after the previous has completed. If an effect fails, the execution stops.
  *
  * @param head First effect to be run.
  * @param tail Rest of the effects.
  */
class EffectSeq(head: Effects, tail: Seq[Effects], override implicit val ec: ExecutionContext) extends EffectBase(ec) {
  override def run(dispatch: (AnyRef) => Unit) = {
    tail.foldLeft(head.run(dispatch)) { (prev, effect) =>
      prev.flatMap(_ => effect.run(dispatch))
    }
  }

  override def >>(that: Effects): EffectSeq = new EffectSeq(head, tail :+ that, ec)

  override def <<(that: Effects): EffectSeq = new EffectSeq(that, head +: tail, ec)

  override def size = 1 + tail.map(_.size).sum

  override def toFuture = head.toFuture

  override def map(g: AnyRef => AnyRef): Effects = new EffectSeq(head.map(g), tail.map(_.map(g)), ec)

  override def flatMap(g: AnyRef => Future[AnyRef]): Effects = new EffectSeq(head.flatMap(g), tail.map(_.flatMap(g)), ec)
}

/**
  * Wraps multiple `Effects` to be executed later. Effects are executed in parallel without any ordering.
  *
  * @param head First effect to be run.
  * @param tail Rest of the effects.
  */
class EffectSet(head: Effects, tail: Set[Effects], override implicit val ec: ExecutionContext) extends EffectBase(ec) {
  override def run(dispatch: (AnyRef) => Unit) = {
    Future.traverse(tail + head)(_.run(dispatch))
  }

  override def +(that: Effects): EffectSet = new EffectSet(head, tail + that, ec)

  override def size = 1 + tail.map(_.size).sum

  override def toFuture = head.toFuture

  override def map(g: AnyRef => AnyRef): Effects = new EffectSet(head.map(g), tail.map(_.map(g)), ec)

  override def flatMap(g: AnyRef => Future[AnyRef]): Effects = new EffectSet(head.flatMap(g), tail.map(_.flatMap(g)), ec)
}

object Effects {
  type EffectF[A] = () => Future[A]

  def apply[A <: AnyRef](f: => Future[A])(implicit ec: ExecutionContext): Effect[A] =
    new Effect(f _, ec)

  def apply(f: => Future[AnyRef], tail: EffectF[AnyRef]*)(implicit ec: ExecutionContext): EffectSet =
    new EffectSet(new Effect(f _, ec), tail.map(f => new Effect(f, ec)).toSet, ec)

  /**
    * Converts a lazy action value into an effect. Typically used in combination of other effects or
    * with `after` to delay execution.
    */
  def action[A <: AnyRef](action: => A)(implicit ec: ExecutionContext): Effect[A] =
    new Effect(() => Future.successful(action), ec)

  implicit def f2effect[A <: AnyRef](f: EffectF[A])(implicit ec: ExecutionContext): Effect[A] = new Effect(f, ec)
}
