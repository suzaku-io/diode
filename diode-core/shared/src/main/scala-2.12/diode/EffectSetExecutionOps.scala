package diode

import scala.collection.generic.CanBuildFrom
import scala.concurrent.Future

trait EffectSetExecutionOps { self: EffectSet =>
  private[diode] def executeWith[A](f: Effect => Future[A]): Future[Set[A]] =
    Future.traverse(tail + head)(f(_))(implicitly[CanBuildFrom[Set[Effect], A, Set[A]]], ec)
}
