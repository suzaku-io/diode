package diode

import scala.collection.generic.CanBuildFrom
import scala.concurrent.{ExecutionContext, Future}

/**
  * Wraps multiple `Effects` to be executed later. Effects are executed in parallel without any ordering.
  *
  * @param head First effect to be run.
  * @param tail Rest of the effects.
  */
class EffectSet(head: Effect, tail: Set[Effect], ec: ExecutionContext) extends EffectBase(ec) {

  private def executeWith[A](f: Effect => Future[A]): Future[Set[A]] =
    Future.traverse(tail + head)(f(_))(implicitly[CanBuildFrom[Set[Effect], A, Set[A]]], ec)

  override def run(dispatch: Any => Unit) =
    executeWith(_.run(dispatch)).map(_ => ())(ec)

  override def +(that: Effect) =
    new EffectSet(head, tail + that, ec)

  override def size =
    head.size + tail.foldLeft(0)((acc, e) => acc + e.size)

  override def toFuture =
    executeWith(_.toFuture)

  override def map[B: ActionType](g: Any => B) =
    new EffectSet(head.map(g), tail.map(_.map(g)), ec)

  override def flatMap[B: ActionType](g: Any => Future[B]) =
    new EffectSet(head.flatMap(g), tail.map(_.flatMap(g)), ec)
}
