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
    * Builds a `RefTo` to a potential value inside a `PotMap`
    *
    * @param key          Identifies the potential value inside the map
    * @param mapTarget    Model reader for the `PotMap` containing the potential value
    * @param updateAction Function to create an update action for the potential value this reference points to
    */
  def apply[K, V, P](key: K, mapTarget: ModelR[_, P])(updateAction: (K, Pot[V]) => AnyRef)(implicit ev: P <:< PotMap[K, V]): RefTo[Pot[V]] =
    RefTo[Pot[V]](mapTarget.zoom(_.get(key)), updateAction(key, _: Pot[V]))

  /**
    * Builds a `RefTo` to a value inside a `PotVector`
    *
    * @param idx          Index to the potential value inside the vector
    * @param vectorTarget Model reader for the `PotVector` containing the potential value
    * @param updateAction Function to create an update action for the potential value this reference points to
    */
  def apply[V, P](idx: Int, vectorTarget: ModelR[_, P])(updateAction: (Int, Pot[V]) => AnyRef)(implicit ev: P <:< PotVector[V]): RefTo[Pot[V]] =
    RefTo[Pot[V]](vectorTarget.zoom(_.get(idx)), updateAction(idx, _: Pot[V]))
}
