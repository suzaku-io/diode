package diode.data

import java.util

import diode.Implicits.runAfterImpl

import scala.annotation.tailrec

class PotVector[V](
    private val fetcher: Fetch[Int],
    private val length: Int,
    private val elems: Array[Option[Pot[V]]]
) extends PotCollection[Int, V] {

  private def enlarge(newSize: Int) = {
    val newArray = Array.ofDim[Option[Pot[V]]](newSize)
    // copy old data
    Array.copy(elems, 0, newArray, 0, elems.length)
    // clear newly allocated space
    for (i <- elems.length until newSize)
      newArray(i) = None
    newArray
  }

  override def updated(idx: Int, value: Pot[V]) = {
    if (idx < 0 || idx >= length)
      throw new IndexOutOfBoundsException
    val newElems = if (idx >= elems.length) {
      // enlarge the array
      enlarge(idx + 1)
    } else {
      elems
    }
    newElems(idx) = Some(value)
    new PotVector(fetcher, length, newElems)
  }

  override def updated(kvs: Iterable[(Int, Pot[V])]): PotVector[V] = {
    val (minIdx, maxIdx) = kvs.foldLeft((Int.MaxValue, Int.MinValue)) {
      case ((min, max), (idx, _)) =>
        (math.min(min, idx), math.max(max, idx))
    }
    if (minIdx < 0 || maxIdx >= length)
      throw new IndexOutOfBoundsException
    val newElems = if (maxIdx >= elems.length) {
      // enlarge the array
      enlarge(maxIdx + 1)
    } else {
      elems
    }
    kvs.foreach { case (idx, value) => newElems(idx) = Some(value) }
    new PotVector(fetcher, length, newElems)
  }

  override def updated(start: Int, values: Iterable[Pot[V]])(implicit num: Numeric[Int]): PotVector[V] = {
    val end = start + values.size
    if (start < 0 || end >= length)
      throw new IndexOutOfBoundsException
    if (end <= start)
      return this

    val newElems = if (end >= elems.length) {
      // enlarge the array
      enlarge(end + 1)
    } else {
      elems
    }
    var idx = start
    values.foreach { value =>
      newElems(idx) = Some(value)
      idx += 1
    }

    new PotVector(fetcher, length, newElems)
  }

  override def seq: Iterable[(Int, Pot[V])] = {
    var out = List.empty[(Int, Pot[V])]
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

  override def iterator: Iterator[(Int, Pot[V])] = new Iterator[(Int, Pot[V])] {
    @tailrec private def findNext(idx: Int): Option[Int] = {
      if (idx >= elems.length)
        None
      else if (elems(idx).isEmpty)
        findNext(idx + 1)
      else
        Some(idx)
    }
    private var current           = findNext(0)
    override def hasNext: Boolean = current.nonEmpty
    override def next(): (Int, Pot[V]) = {
      val idx = current.get
      current = findNext(idx + 1)
      idx -> elems(idx).get
    }
  }

  override def remove(idx: Int) = {
    elems(idx) = None
    this
  }

  override def refresh(idx: Int): Unit = {
    if (idx < 0 || idx >= length)
      throw new IndexOutOfBoundsException
    // perform fetch asynchronously
    runAfterImpl.runAfter(0)(fetcher.fetch(idx))
  }

  override def refresh(indices: Iterable[Int]): Unit = {
    if (indices.exists(idx => idx < 0 || idx >= length))
      throw new IndexOutOfBoundsException
    // perform fetch asynchronously
    runAfterImpl.runAfter(0)(fetcher.fetch(indices))
  }

  override def clear =
    new PotVector(fetcher, length, Array.empty[Option[Pot[V]]])

  override def get(idx: Int): Pot[V] = {
    if (idx < 0 || idx >= length)
      throw new IndexOutOfBoundsException
    if (idx >= elems.length || elems(idx).isEmpty) {
      refresh(idx)
      Pending().asInstanceOf[Pot[V]]
    } else {
      elems(idx).get
    }
  }

  override def map(f: (Int, Pot[V]) => Pot[V]): PotVector[V] = {
    val newElems = new Array[Option[Pot[V]]](elems.length)
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

  def slice(start: Int, end: Int): Seq[Pot[V]] = {
    if (start < 0 || end >= length)
      throw new IndexOutOfBoundsException
    if (end <= start)
      return Seq()
    var missing = List.empty[Int]
    val values = (start until end).map { idx =>
      if (idx >= elems.length || elems(idx).isEmpty) {
        missing ::= idx
        Pending().asInstanceOf[Pot[V]]
      } else {
        elems(idx).get
      }
    }
    // are all missing?
    if (missing.size == end - start)
      runAfterImpl.runAfter(0)(fetcher.fetch(start, end))
    else if (missing.nonEmpty)
      refresh(missing)

    values
  }

  def resized(newLength: Int) =
    new PotVector(fetcher, newLength, if (newLength < elems.length) util.Arrays.copyOf(elems, newLength) else elems)

  def contains(idx: Int) = {
    if (idx < 0 || idx >= length)
      throw new IndexOutOfBoundsException
    elems(idx).isDefined
  }
}

object PotVector {
  def apply[V](fetcher: Fetch[Int], length: Int, elems: Seq[Pot[V]] = Seq.empty[Pot[V]]) =
    new PotVector[V](fetcher, length, elems.map(Some(_)).toArray)
}
