package diode.util

/**
  * Provides ways to asynchronously fetch value(s) based on key(s)
  */
trait Fetch[K] {
  /**
    * Start fetching a single value for `key`
    */
  def fetch(key: K): Unit

  /**
    * Start fetching a range of values from `start` to `end`
    */
  def fetch(start: K, end: K): Unit

  /**
    * Start fetching a set of values
    */
  def fetch(keys: Traversable[K]): Unit
}

trait PotCollection[K, V <: Pot[_]] {
  def updated(key: K, value: V): PotCollection[K, V]

  def updated(start: K, values: Traversable[V])(implicit num: Numeric[K]): PotCollection[K, V]

  def updated(kvs: Traversable[(K, V)]): PotCollection[K, V]

  def updated(coll: PotCollection[K, V]): PotCollection[K, V] = updated(coll.seq)

  def remove(key: K): PotCollection[K, V]

  def apply(key: K): V = get(key)

  def get(key: K): V

  def seq: Traversable[(K, V)]

  def refresh(key: K): Unit

  def refresh(keys: Traversable[K]): Unit

  def clear: PotCollection[K, V]

  def map(f: (K, V) => V): PotCollection[K, V]
}
