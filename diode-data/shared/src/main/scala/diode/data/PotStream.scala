package diode.data

import diode.Implicits.runAfterImpl

import scala.annotation.tailrec

final case class StreamValue[K, V](key: K,
                                   value: V,
                                   stream: PotStream[K, V],
                                   prevKey: Option[K] = None,
                                   nextKey: Option[K] = None
) {
  def apply() = value

  def prev = stream.get(prevKey)

  def next = stream.get(nextKey)
}

class PotStream[K, V](
    private val fetcher: Fetch[K],
    val elems: Map[K, StreamValue[K, V]],
    val headKeyOption: Option[K],
    val lastKeyOption: Option[K]
) {
  private def updatedHead(key: K) =
    headKeyOption.orElse(Some(key))

  private def updatedLast(key: K) =
    lastKeyOption.orElse(Some(key))

  def updated(key: K, value: V): PotStream[K, V] = {
    if (!elems.contains(key))
      throw new NoSuchElementException("Can only update existing elements")

    new PotStream(fetcher, elems.updated(key, elems(key).copy(value = value)), headKeyOption, lastKeyOption)
  }

  def append(key: K, value: V): PotStream[K, V] = append(List(key -> value))

  def append(kvs: Seq[(K, V)]): PotStream[K, V] = {
    if (kvs.isEmpty)
      this
    else {
      @tailrec
      def buildStream(prev: Option[K],
                      next: Option[K],
                      head: (K, V),
                      tail: Seq[(K, V)],
                      acc: List[StreamValue[K, V]]
      ): List[StreamValue[K, V]] = {
        if (tail.isEmpty) {
          StreamValue(head._1, head._2, this, prev, next) :: acc
        } else {
          buildStream(
            Some(head._1),
            tail.tail.headOption.map(_._1),
            tail.head,
            tail.tail,
            StreamValue(head._1, head._2, this, prev, next) :: acc
          )
        }
      }

      val newValues = buildStream(lastKeyOption, kvs.tail.headOption.map(_._1), kvs.head, kvs.tail, Nil)
      val firstKey  = kvs.head._1
      val lastKey   = newValues.head.key
      val headKey   = headKeyOption.getOrElse(firstKey)
      // join new values and update the previously last value to point to the first of the new values
      val newElems: Map[K, StreamValue[K, V]] =
        elems ++ newValues.map(sv => sv.key -> sv) ++ lastKeyOption.map(lk => lk -> elems(lk).copy(nextKey = Some(firstKey)))
      new PotStream(fetcher, newElems, updatedHead(headKey), Some(lastKey))
    }
  }

  def prepend(key: K, value: V): PotStream[K, V] = prepend(List(key -> value))

  def prepend(kvs: Seq[(K, V)]): PotStream[K, V] = {
    if (kvs.isEmpty)
      this
    else {
      @tailrec
      def buildStream(prev: Option[K],
                      next: Option[K],
                      head: (K, V),
                      tail: Seq[(K, V)],
                      acc: List[StreamValue[K, V]]
      ): List[StreamValue[K, V]] = {
        if (tail.isEmpty) {
          StreamValue(head._1, head._2, this, prev, next) :: acc
        } else {
          buildStream(
            tail.tail.headOption.map(_._1),
            Some(head._1),
            tail.head,
            tail.tail,
            StreamValue(head._1, head._2, this, prev, next) :: acc
          )
        }
      }

      val reversedKvs = kvs.reverse
      val newValues =
        buildStream(reversedKvs.tail.headOption.map(_._1), headKeyOption, reversedKvs.head, reversedKvs.tail, Nil)
      val firstKey = reversedKvs.head._1
      val headKey  = kvs.head._1
      val lastKey  = lastKeyOption.getOrElse(headKey)
      // join new values and update the previously head value to point to the last of the new values
      val newElems: Map[K, StreamValue[K, V]] =
        elems ++ newValues.map(sv => sv.key -> sv) ++ headKeyOption.map(hk => hk -> elems(hk).copy(prevKey = Some(firstKey)))
      new PotStream(fetcher, newElems, Some(headKey), updatedLast(lastKey))
    }
  }

  def apply(key: K): V =
    get(key).value

  def get(key: K): StreamValue[K, V] =
    elems(key)

  def get(key: Option[K]): Option[StreamValue[K, V]] =
    key.flatMap(elems.get)

  def contains(key: K): Boolean =
    elems.contains(key)

  def clear: PotStream[K, V] =
    PotStream(fetcher)

  def refresh(key: K): Unit = {
    // perform fetch asynchronously
    runAfterImpl.runAfter(0)(fetcher.fetch(key))
  }

  def refreshNext(count: Int = 1): Unit = {
    // perform fetch asynchronously
    runAfterImpl.runAfter(0)(fetcher.fetchNext(lastKeyOption.get, count))
  }

  def refreshPrev(count: Int = 1): Unit = {
    // perform fetch asynchronously
    runAfterImpl.runAfter(0)(fetcher.fetchPrev(headKeyOption.get, count))
  }

  def remove(key: K): PotStream[K, V] = {
    if (elems.isEmpty || !elems.contains(key))
      throw new NoSuchElementException

    // fix prev/next references in prev/next values
    val prev = elems(key).prevKey
    val next = elems(key).nextKey
    new PotStream(
      fetcher,
      elems - key
        ++ prev.map(k => k -> elems(k).copy(nextKey = next))
        ++ next.map(k => k -> elems(k).copy(prevKey = prev)),
      headKeyOption.filterNot(_ == key).orElse(elems(headKeyOption.get).nextKey),
      lastKeyOption.filterNot(_ == key).orElse(elems(lastKeyOption.get).prevKey)
    )
  }

  def head: StreamValue[K, V] =
    elems(headKeyOption.get)

  def headOption: Option[StreamValue[K, V]] =
    headKeyOption.map(elems)

  def last: StreamValue[K, V] =
    elems(lastKeyOption.get)

  def lastOption: Option[StreamValue[K, V]] =
    lastKeyOption.map(elems)

  def tail: PotStream[K, V] = {
    if (elems.isEmpty)
      throw new UnsupportedOperationException("empty.tail")
    remove(headKeyOption.get)
  }

  def init: PotStream[K, V] = {
    if (elems.isEmpty)
      throw new UnsupportedOperationException("empty.init")
    remove(lastKeyOption.get)
  }

  def map(f: (K, V) => V): PotStream[K, V] =
    new PotStream(fetcher, elems.map { case (k, sv) => k -> sv.copy(value = f(k, sv.value)) }, headKeyOption, lastKeyOption)

  def seq: Iterable[(K, V)] = {
    var res = List.empty[(K, V)]
    var key = lastKeyOption
    while (key.isDefined) {
      val v = elems(key.get)
      res ::= v.key -> v.value
      key = v.prevKey
    }
    res
  }

  def iterator: Iterator[(K, V)] = new Iterator[(K, V)] {
    private var current           = headOption
    override def hasNext: Boolean = current.nonEmpty
    override def next(): (K, V) = {
      val r = current.map(sv => sv.key -> sv.value).get
      current = current.flatMap(_.nextKey).map(elems)
      r
    }
  }

  def size = elems.size

  def keys = elems.keys
}

object PotStream {
  def apply[K, V](fetcher: Fetch[K], elems: Seq[(K, V)] = Seq.empty): PotStream[K, V] = {
    new PotStream(fetcher, Map(), None, None).append(elems)
  }
}
