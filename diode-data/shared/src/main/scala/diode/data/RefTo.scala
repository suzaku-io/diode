package diode.data

import diode._

/**
  * Provides a reference to a value elsewhere in the model.
  *
  * @param target
  *   Model reader for the referred value
  * @param updated
  *   Function to create an update action for the value this reference points to
  */
class RefTo[V](val target: ModelRO[V], val updated: V => Action) {
  def apply() = target()
}

object RefTo {

  /**
    * Provides a read-only reference that always produces a `NoAction` when an update is requested.
    *
    * @param target
    *   Model reader for the referred value
    */
  @inline def apply[V](target: ModelRO[V]): RefTo[V] =
    new RefTo(target, (_: V) => NoAction)

  @inline def apply[V](target: ModelRO[V], updated: V => Action): RefTo[V] =
    new RefTo(target, updated)

  /**
    * Builds a `RefTo` to a potential value inside a `PotCollection`
    *
    * @param key
    *   Identifies the potential value inside the collection
    * @param collTarget
    *   Model reader for the `PotCollection` containing the potential value
    * @param updated
    *   Function to create an update action for the potential value this reference points to
    */
  @inline
  def apply[K, V, P](key: K, collTarget: ModelRO[P])(updated: (K, Pot[V]) => Action)(implicit
      ev: P <:< PotCollection[K, V]
  ): RefTo[Pot[V]] =
    new RefTo[Pot[V]](collTarget.zoom(_.get(key)), updated(key, _: Pot[V]))

  /**
    * Builds a `RefTo` to a value inside a `PotStream`
    *
    * @param key
    *   Identifies the value inside the stream
    * @param streamTarget
    *   Model reader for the `PotStream` containing the value
    * @param updated
    *   Function to create an update action for the potential value this reference points to
    */
  @inline
  def stream[K, V, P](key: K, streamTarget: ModelRO[P])(
      updated: (K, V) => Action
  )(implicit ev: P <:< PotStream[K, V], feq: FastEq[_ >: V]): RefTo[V] =
    new RefTo[V](streamTarget.zoom(_.apply(key)), updated(key, _: V))
}
