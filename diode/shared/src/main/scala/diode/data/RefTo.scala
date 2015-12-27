package diode.data

import diode.ModelR

import scala.language.existentials

/**
  * Provides a reference to a value elsewhere in the model.
  *
  * @param target       Model reader for the referred value
  * @param updateAction Function to create an update action for the value this reference points to
  */
case class RefTo[V](target: ModelR[_, V], updateAction: V => AnyRef) {
  def apply() = target()
}

object RefTo {
  /**
    * Provides a read-only reference that always produces a None action when an update is requested.
    *
    * @param target Model reader for the referred value
    */
  def apply[V](target: ModelR[_, V]): RefTo[V] =
    RefTo(target, (_: V) => None)

  /**
    * Builds a `RefTo` to a potential value inside a `PotCollection`
    *
    * @param key          Identifies the potential value inside the collection
    * @param collTarget   Model reader for the `PotCollection` containing the potential value
    * @param updateAction Function to create an update action for the potential value this reference points to
    */
  def apply[K, V, P](key: K, collTarget: ModelR[_, P])(updateAction: (K, Pot[V]) => AnyRef)(implicit ev: P <:< PotCollection[K, V]): RefTo[Pot[V]] =
    RefTo[Pot[V]](collTarget.zoom(_.get(key)), updateAction(key, _: Pot[V]))

  /**
    * Builds a `RefTo` to a value inside a `PotStream`
    *
    * @param key          Identifies the value inside the stream
    * @param streamTarget Model reader for the `PotStream` containing the value
    * @param updateAction Function to create an update action for the potential value this reference points to
    */
  def stream[K, V, P](key: K, streamTarget: ModelR[_, P])(updateAction: (K, V) => AnyRef)(implicit ev: P <:< PotStream[K, V]): RefTo[V] =
    RefTo[V](streamTarget.zoom(_.apply(key)), updateAction(key, _: V))
}
