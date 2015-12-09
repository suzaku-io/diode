package diode.util

import java.util

class PotVector[V <: Pot[_]](
  private val fetcher: Fetch[Int],
  private val length: Int,
  private val elems: Array[Option[V]] = Array.empty[Option[V]]
) extends PotCollection[Int, V] {

  private def enlarge[V](newSize: Int) = {
    val newArray = Array.fill[Option[V]](newSize)(None)
    Array.copy(elems, 0, newArray, 0, elems.length)
    newArray
  }

  override def updated(idx: Int, value: V) = {
    if (idx < 0 || idx >= length)
      throw new IndexOutOfBoundsException
    val newElems = if (idx >= elems.length) {
      // enlarge the array
      enlarge[V](idx + 1)
    } else {
      elems.asInstanceOf[Array[Option[V]]]
    }
    newElems(idx) = Some(value)
    new PotVector(fetcher, length, newElems)
  }

  override def updated(kvs: Traversable[(Int, V)]) = {
    val (minIdx, maxIdx) = kvs.foldLeft((Int.MaxValue, Int.MinValue)) { case ((min, max), (idx, _)) =>
      (math.min(min, idx), math.max(max, idx))
    }
    if (minIdx < 0 || maxIdx >= length)
      throw new IndexOutOfBoundsException
    val newElems = if (maxIdx >= elems.length) {
      // enlarge the array
      enlarge[V](maxIdx + 1)
    } else {
      elems.asInstanceOf[Array[Option[V]]]
    }
    kvs.foreach { case (idx, value) => newElems(idx) = Some(value) }
    new PotVector(fetcher, length, newElems)
  }

  override def updated(start: Int, values: Traversable[V])(implicit num: Numeric[Int]): PotVector[V] = {
    val end = start + values.size
    if (start < 0 || end >= length)
      throw new IndexOutOfBoundsException
    if (end <= start)
      return this.asInstanceOf[PotVector[V]]

    val newElems = if (end >= elems.length) {
      // enlarge the array
      enlarge[V](end + 1)
    } else {
      elems.asInstanceOf[Array[Option[V]]]
    }
    var idx = start
    values.foreach { value =>
      newElems(idx) = Some(value)
      idx += 1
    }

    new PotVector(fetcher, length, newElems)
  }

  override def seq: Traversable[(Int, V)] = {
    var out = List.empty[(Int, V)]
    // iterate in reverse order to get the output list in correct order
    for (idx <- elems.indices.reverse) {
      elems(idx) match {
        case Some(value) =>
          out ::= idx -> value
        case _ =>
      }
    }
    out
  }

  override def remove(idx: Int) = {
    elems(idx) = None
    this
  }

  override def refresh(idx: Int): Unit = {
    if (idx < 0 || idx >= length)
      throw new IndexOutOfBoundsException
    if (idx >= elems.length || elems(idx).isEmpty || !elems(idx).contains(Unavailable))
      fetcher.fetch(idx)
  }

  override def refresh(indices: Traversable[Int]): Unit = {
    val toFetch = indices.flatMap { idx =>
      if (idx < 0 || idx >= length)
        throw new IndexOutOfBoundsException
      if (idx >= elems.length || elems(idx).isEmpty || !elems(idx).contains(Unavailable))
        Some(idx)
      else
        None
    }
    fetcher.fetch(toFetch)
  }

  override def clear =
    new PotVector(fetcher, length, Array.empty[Option[V]])

  override def get(idx: Int): V = {
    if (idx < 0 || idx >= length)
      throw new IndexOutOfBoundsException
    if (idx >= elems.length || elems(idx).isEmpty) {
      fetcher.fetch(idx)
      Pending().asInstanceOf[V]
    } else {
      elems(idx).get
    }
  }

  override def map(f: (Int, V) => V) = {
    val newElems = new Array[Option[V]](elems.length)
    for (idx <- elems.indices) {
      newElems(idx) = elems(idx) match {
        case Some(value) =>
          Some(f(idx, value))
        case None =>
          None
      }
    }
    new PotVector(fetcher, length, newElems)
  }

  def slice(start: Int, end: Int): Seq[V] = {
    if (start < 0 || end >= length)
      throw new IndexOutOfBoundsException
    if (end <= start)
      return Seq()
    var missing = List.empty[Int]
    val values = (start until end).map { idx =>
      if (idx >= elems.length || elems(idx).isEmpty) {
        missing ::= idx
        Pending().asInstanceOf[V]
      } else {
        elems(idx).get
      }
    }
    // are all missing?
    if (missing.size == end - start)
      fetcher.fetch(start, end)
    else if (missing.nonEmpty)
      fetcher.fetch(missing)

    values
  }

  def resized(newLength: Int) =
    new PotVector(fetcher, newLength, if (newLength < elems.length) util.Arrays.copyOf(elems, newLength) else elems)
}
