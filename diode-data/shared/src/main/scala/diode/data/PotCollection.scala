package diode.data

/**
  * Provides methods to asynchronously fetch value(s) based on key(s)
  */
trait Fetch[K] {

  /**
    * Start fetching a single value for `key`
    */
  def fetch(key: K): Unit

  /**
    * Start fetching a set of values
    */
  def fetch(keys: Iterable[K]): Unit

  /**
    * Start fetching a range of values from `start` to `end`
    */
  def fetch(start: K, end: K): Unit = {}

  /**
    * Start fetching values that precede `key`
    */
  def fetchPrev(key: K, count: Int = 1): Unit = {}

  /**
    * Start fetching values that follow `key`
    */
  def fetchNext(key: K, count: Int = 1): Unit = {}
}

/**
  * Trait defining common functionality for all potential collections. All values inside the collection are wrapped in
  * `Pot[V]`
  *
  * @tparam K
  *   Type of the key
  * @tparam V
  *   Type of the potential value
  */
trait PotCollection[K, V] {
  def updated(key: K, value: Pot[V]): PotCollection[K, V]

  def updated(start: K, values: Iterable[Pot[V]])(implicit num: Numeric[K]): PotCollection[K, V]

  def updated(kvs: Iterable[(K, Pot[V])]): PotCollection[K, V]

  def updated(coll: PotCollection[K, V]): PotCollection[K, V] = updated(coll.seq)

  def remove(key: K): PotCollection[K, V]

  def apply(key: K): Pot[V] = get(key)

  def get(key: K): Pot[V]

  def seq: Iterable[(K, Pot[V])]

  def iterator: Iterator[(K, Pot[V])]

  def refresh(key: K): Unit

  def refresh(keys: Iterable[K]): Unit

  def clear: PotCollection[K, V]

  def map(f: (K, Pot[V]) => Pot[V]): PotCollection[K, V]
}
