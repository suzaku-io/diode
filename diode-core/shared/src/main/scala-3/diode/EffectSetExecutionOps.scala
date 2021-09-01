package diode

import scala.concurrent.Future
import scala.collection.BuildFrom

trait EffectSetExecutionOps { self: EffectSet =>
  private[diode] def executeWith[A](f: Effect => Future[A]): Future[Set[A]] =
    Future.traverse(tail + head)(f(_))(
      summon[BuildFrom[Set[Effect], A, Set[A]]],
      (EffectSetExecutionOps.this: diode.EffectSet).ec
    )
}
