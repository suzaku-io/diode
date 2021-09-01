package diode.data

import diode.Implicits.runAfterImpl

class PotMap[K, V](
    private val fetcher: Fetch[K],
    private val elems: Map[K, Pot[V]]
) extends PotCollection[K, V] {

  override def updated(key: K, value: Pot[V]): PotMap[K, V] =
    new PotMap(fetcher, elems + (key -> value))

  override def updated(kvs: Iterable[(K, Pot[V])]): PotMap[K, V] =
    new PotMap(fetcher, elems ++ kvs)

  override def updated(start: K, values: Iterable[Pot[V]])(implicit num: Numeric[K]): PotMap[K, V] = {
    if (values.isEmpty)
      this
    else {
      val newElems = values.tail.scanLeft((start, values.head)) {
        case ((idx, value), next) => (num.plus(idx, num.one), next)
      }
      new PotMap(fetcher, elems ++ newElems)
    }
  }

  override def seq: Iterable[(K, Pot[V])] = elems

  override def iterator: Iterator[(K, Pot[V])] = elems.iterator

  override def remove(key: K): PotMap[K, V] =
    new PotMap(fetcher, elems - key)

  override def refresh(key: K): Unit = {
    // perform fetch asynchronously
    runAfterImpl.runAfter(0)(fetcher.fetch(key))
  }

  override def refresh(keys: Iterable[K]): Unit = {
    // perform fetch asynchronously
    runAfterImpl.runAfter(0)(fetcher.fetch(keys))
  }

  override def clear =
    new PotMap(fetcher, Map.empty[K, Pot[V]])

  override def get(key: K) = {
    elems.get(key) match {
      case Some(elem) if elem.state == PotState.PotEmpty =>
        refresh(key)
        Pending().asInstanceOf[Pot[V]]
      case Some(elem) =>
        elem
      case None =>
        refresh(key)
        Pending().asInstanceOf[Pot[V]]
    }
  }

  override def map(f: (K, Pot[V]) => Pot[V]): PotMap[K, V] = {
    new PotMap(fetcher, elems.map(kv => (kv._1, f(kv._1, kv._2))))
  }

  def +(kv: (K, Pot[V])): PotMap[K, V] = updated(kv._1, kv._2)

  def ++(xs: Iterable[(K, Pot[V])]) = updated(xs)

  def -(key: K) = remove(key)

  def get(keys: Iterable[K]): Map[K, Pot[V]] = {
    var toFetch = List.empty[K]
    val values: Map[K, Pot[V]] =
      keys.map { key =>
        elems.get(key) match {
          case Some(elem) if elem.state == PotState.PotEmpty =>
            toFetch ::= key
            (key, Pending().asInstanceOf[Pot[V]])
          case Some(elem) =>
            (key, elem)
          case None =>
            toFetch ::= key
            (key, Pending().asInstanceOf[Pot[V]])
        }
      }.toMap

    if (toFetch.nonEmpty) {
      refresh(toFetch)
    }
    values
  }

  def size = elems.size

  def keys = elems.keys

  def keySet = elems.keySet
}

object PotMap {
  def apply[K, V](fetcher: Fetch[K], elems: Map[K, Pot[V]] = Map.empty[K, Pot[V]]) = new PotMap[K, V](fetcher, elems)
}
