package diode.data

class PotMap[K, V <: Pot[_]](
  private val fetcher: Fetch[K],
  private val elems: Map[K, V] = Map.empty[K, V]
) extends PotCollection[K, V] {

  override def updated(key: K, value: V): PotMap[K, V] =
    new PotMap(fetcher, elems + (key -> value))

  override def updated(kvs: Traversable[(K, V)]) =
    new PotMap(fetcher, elems ++ kvs)

  override def updated(start: K, values: Traversable[V])(implicit num: Numeric[K]): PotMap[K, V] = {
    if (values.isEmpty)
      this
    else {
      val newElems = values.tail.scanLeft((start, values.head)) {
        case ((idx, value), next) => (num.plus(idx, num.one), next)
      }
      new PotMap(fetcher, elems ++ newElems)
    }
  }

  override def seq: Traversable[(K, V)] = elems

  override def remove(key: K) =
    new PotMap(fetcher, elems - key)

  override def refresh(key: K): Unit = {
    elems.get(key) match {
      case Some(elem) if elem.state == PotState.PotUnavailable =>
      // do nothing for Unavailable
      case _ =>
        fetcher.fetch(key)
    }
  }

  override def refresh(keys: Traversable[K]): Unit = {
    val toFetch = keys.flatMap { key =>
      elems.get(key) match {
        case Some(elem) if elem.state == PotState.PotUnavailable =>
          // do nothing for Unavailable
          None
        case _ =>
          Some(key)
      }
    }
    fetcher.fetch(toFetch)
  }

  override def clear =
    new PotMap(fetcher, Map.empty[K, V])

  override def get(key: K) = {
    elems.get(key) match {
      case Some(elem) if elem.state == PotState.PotEmpty =>
        fetcher.fetch(key)
        Pending().asInstanceOf[V]
      case Some(elem) =>
        elem
      case None =>
        fetcher.fetch(key)
        Pending().asInstanceOf[V]
    }
  }

  override def map(f: (K, V) => V) = {
    new PotMap(fetcher, elems.map(kv => (kv._1, f(kv._1, kv._2))))
  }

  def +(kv: (K, V)): PotMap[K, V] = updated(kv._1, kv._2)

  def ++(xs: Traversable[(K, V)]) = updated(xs)

  def -(key: K) = remove(key)

  def get(keys: Traversable[K]): Map[K, V] = {
    var toFetch = List.empty[K]
    val values: Map[K, V] = keys.map { key =>
      elems.get(key) match {
        case Some(elem) if elem.state == PotState.PotEmpty =>
          toFetch ::= key
          (key, Pending().asInstanceOf[V])
        case Some(elem) =>
          (key, elem)
        case None =>
          toFetch ::= key
          (key, Pending().asInstanceOf[V])
      }
    }(collection.breakOut)
    fetcher.fetch(toFetch)
    values
  }

  def iterator: Iterator[(K, V)] = elems.iterator

  def size = elems.size

  def keys = elems.keys
}
