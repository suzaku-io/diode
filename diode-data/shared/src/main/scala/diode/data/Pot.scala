package diode.data

import java.util.Date

import diode.Monad
import diode.util._

import scala.util.{Failure, Success, Try}

sealed trait PotState

object PotState {

  case object PotEmpty extends PotState

  case object PotUnavailable extends PotState

  case object PotReady extends PotState

  case object PotPending extends PotState

  case object PotFailed extends PotState

}

/**
  * Represents a potential value that may be in different states.
  *
  * @define pot
  *   [[Pot]]
  * @define ready
  *   [[Ready]]
  * @define empty
  *   [[Empty]]
  */
sealed abstract class Pot[+A] extends Product with Serializable { self =>

  def get: A
  def isEmpty: Boolean
  def isPending: Boolean
  def isStale: Boolean
  def isFailed: Boolean
  def isReady = !isEmpty && !isStale
  def isUnavailable: Boolean
  def ready[B >: A](value: B): Pot[B] = Ready(value)
  def pending(startTime: Long = Pot.currentTime): Pot[A]
  def fail(exception: Throwable): Pot[A]
  def unavailable() = Unavailable
  def state: PotState

  /**
    * Returns false if the pot is Empty, true otherwise.
    *
    * @note
    *   Implemented here to avoid the implicit conversion to Iterable.
    */
  final def nonEmpty = !isEmpty

  @inline final def getOrElse[B >: A](default: => B): B =
    if (isEmpty) default else this.get

  /**
    * Returns a Ready containing the result of applying $f to this Pot's value if this Pot is nonempty. Otherwise return
    * current Pot.
    *
    * @note
    *   This is similar to `flatMap` except here, $f does not need to wrap its result in a pot.
    * @param f
    *   the function to apply
    * @see
    *   flatMap
    * @see
    *   foreach
    */
  @noinline final def map[B](f: A => B): Pot[B] = this match {
    case Empty              => Empty
    case Ready(x)           => Ready(f(x))
    case Pending(t)         => Pending(t)
    case PendingStale(x, t) => PendingStale(f(x), t)
    case Failed(e)          => Failed(e)
    case FailedStale(x, e)  => FailedStale(f(x), e)
    case Unavailable        => Unavailable
  }

  /**
    * Returns the result of applying $f to this Pot's value if the Pot is nonempty. Otherwise, evaluates expression
    * `ifEmpty`.
    *
    * @note
    *   This is equivalent to `Pot map f getOrElse ifEmpty`.
    * @param ifEmpty
    *   the expression to evaluate if empty.
    * @param f
    *   the function to apply if nonempty.
    */
  @inline final def fold[B](ifEmpty: => B)(f: A => B): B =
    if (isEmpty) ifEmpty else f(this.get)

  /**
    * Returns the result of applying $f to this Pot's value if this Pot is nonempty. Returns current Pot if this Pot does not
    * have a value. Slightly different from `map` in that $f is expected to return a pot (which could be Empty).
    *
    * @param f
    *   the function to apply
    * @see
    *   map
    * @see
    *   foreach
    */
  @noinline def flatMap[B](f: A => Pot[B]): Pot[B] = map(f).flatten

  @noinline def flatten[B](implicit ev: A <:< Pot[B]): Pot[B] = this match {
    case Empty      => Empty
    case Ready(x)   => x
    case Pending(t) => Pending(t)
    case PendingStale(x, t) =>
      ev(x) match {
        case Empty              => Pending(t)
        case Ready(y)           => PendingStale(y, t)
        case Pending(s)         => Pending(math.min(s, t))
        case PendingStale(y, s) => PendingStale(y, math.min(s, t))
        case other              => other
      }
    case Failed(e) => Failed(e)
    case FailedStale(x, e) =>
      ev(x) match {
        case Empty => Failed(e)
        case other => other
      }
    case Unavailable => Unavailable
  }

  /**
    * Returns this Pot if it is nonempty '''and''' applying the predicate $p to this Pot's value returns true. Otherwise,
    * return Empty.
    *
    * @param p
    *   the predicate used for testing.
    */
  @inline final def filter(p: A => Boolean): Pot[A] =
    if (isEmpty || p(this.get)) this else Empty

  /**
    * Returns this Pot if it is nonempty '''and''' applying the predicate $p to this Pot's value returns false. Otherwise,
    * return Empty.
    *
    * @param p
    *   the predicate used for testing.
    */
  @inline final def filterNot(p: A => Boolean): Pot[A] =
    if (isEmpty || !p(this.get)) this else Empty

  /**
    * Necessary to keep Pot from being implicitly converted to [[scala.collection.Iterable]] in `for` comprehensions.
    */
  @inline final def withFilter(p: A => Boolean): WithFilter = new WithFilter(p)

  /**
    * We need a whole WithFilter class to honor the "doesn't create a new collection" contract even though it seems unlikely
    * to matter much in a collection with max size 1.
    */
  class WithFilter(p: A => Boolean) {
    def map[B](f: A => B): Pot[B] = self filter p map f

    def flatMap[B](f: A => Pot[B]): Pot[B] = self filter p flatMap f

    def foreach[U](f: A => U): Unit = self filter p foreach f

    def withFilter(q: A => Boolean): WithFilter = new WithFilter(x => p(x) && q(x))
  }

  /**
    * Tests whether the pot contains a given value as an element.
    *
    * @example
    *   {{{
    *   // Returns true because Ready instance contains string "something" which equals "something".
    *   Ready("something") contains "something"
    *
    *   // Returns false because "something" != "anything".
    *   Ready("something") contains "anything"
    *
    *   // Returns false when method called on Empty.
    *   Empty contains "anything"
    *   }}}
    * @param elem
    *   the element to test.
    * @return
    *   `true` if the pot has an element that is equal (as determined by `==`) to `elem`, `false` otherwise.
    */
  final def contains[A1 >: A](elem: A1): Boolean =
    !isEmpty && this.get == elem

  /**
    * Returns true if this pot is nonempty '''and''' the predicate $p returns true when applied to this Pot's value.
    * Otherwise, returns false.
    *
    * @param p
    *   the predicate to test
    */
  @inline final def exists(p: A => Boolean): Boolean =
    !isEmpty && p(this.get)

  /**
    * Returns true if this pot is empty '''or''' the predicate $p returns true when applied to this Pot's value.
    *
    * @param p
    *   the predicate to test
    */
  @inline final def forall(p: A => Boolean): Boolean = isEmpty || p(this.get)

  /**
    * Apply the given procedure $f to the pot's value, if it is nonempty. Otherwise, do nothing.
    *
    * @param f
    *   the procedure to apply.
    * @see
    *   map
    * @see
    *   flatMap
    */
  @inline final def foreach[U](f: A => U): Unit = {
    if (!isEmpty) f(this.get)
  }

  /**
    * Returns a Ready containing the result of applying `pf` to this Pot's contained value, '''if''' this pot is nonempty
    * '''and''' `pf` is defined for that value. Returns Empty otherwise.
    *
    * @example
    *   {{{
    * // Returns Ready(HTTP) because the partial function covers the case.
    * Ready("http") collect {case "http" => "HTTP"}
    *
    * // Returns Empty because the partial function doesn't cover the case.
    * Ready("ftp") collect {case "http" => "HTTP"}
    *
    * // Returns Empty because Empty is passed to the collect method.
    * Empty collect {case value => value}
    *   }}}
    * @param pf
    *   the partial function.
    * @return
    *   the result of applying `pf` to this Pot's value (if possible), or Empty.
    */
  @inline final def collect[B](pf: PartialFunction[A, B]): Pot[B] =
    if (!isEmpty) pf.lift(this.get).map(b => Ready(b)).getOrElse(Empty) else Empty

  /**
    * Returns this Pot if it is nonempty, otherwise return the result of evaluating `alternative`.
    *
    * @param alternative
    *   the alternative expression.
    */
  @inline final def orElse[B >: A](alternative: => Pot[B]): Pot[B] =
    if (isEmpty) alternative else this

  /**
    * Applies the given function `f` if this is a `Failure`, otherwise returns this if this is a `Success`. This is like
    * `flatMap` for the exception.
    */
  def recoverWith[B >: A](f: PartialFunction[Throwable, Pot[B]]): Pot[B] = this

  /**
    * Applies the given function `f` if this is a `Failure`, otherwise returns this if this is a `Success`. This is like map
    * for the exception.
    */
  def recover[B >: A](f: PartialFunction[Throwable, B]): Pot[B] = this

  def exceptionOption = Option.empty[Throwable]

  /**
    * Returns a singleton iterator returning the Pot's value if it is nonempty, or an empty iterator if the pot is empty.
    */
  def iterator: Iterator[A] =
    if (isEmpty) collection.Iterator.empty else collection.Iterator.single(this.get)

  /**
    * Returns `None` if this is empty or a `Some` containing the value otherwise.
    */
  @inline def toOption: Option[A] = if (!isEmpty) Some(get) else None

  /**
    * Returns `Failure` if this has failed or a `Success` containing the value otherwise.
    */
  def toTry: Try[A] = {
    if (isEmpty)
      Failure(new NoSuchElementException)
    else
      this match {
        case Failed(ex)         => Failure(ex)
        case FailedStale(_, ex) => Failure(ex)
        case _                  => Success(get)
      }
  }

  /**
    * Returns a singleton list containing the Pot's value if it is nonempty, or the empty list if the Pot is empty.
    */
  def toList: List[A] =
    if (isEmpty) List() else new ::(this.get, Nil)

  /**
    * Returns a [[scala.util.Left]] containing the given argument `left` if this Pot is empty, or a [[scala.util.Right]]
    * containing this Pot's value if this is nonempty.
    *
    * @param left
    *   the expression to evaluate and return if this is empty
    * @see
    *   toLeft
    */
  @inline final def toRight[X](left: => X) =
    if (isEmpty) Left(left) else Right(this.get)

  /**
    * Returns a [[scala.util.Right]] containing the given argument `right` if this is empty, or a [[scala.util.Left]]
    * containing this Pot's value if this Pot is nonempty.
    *
    * @param right
    *   the expression to evaluate and return if this is empty
    * @see
    *   toRight
    */
  @inline final def toLeft[X](right: => X) =
    if (isEmpty) Right(right) else Left(this.get)
}

object Pot {

  import scala.language.implicitConversions

  /**
    * An implicit conversion that converts an option to an iterable value
    */
  implicit def pot2Iterable[A](pot: Pot[A]): Iterable[A] = pot.toList

  /**
    * A Pot factory which returns `Empty` in a manner consistent with the collections hierarchy.
    */
  def empty[A]: Pot[A] = Empty

  def fromOption[A](a: Option[A]): Pot[A] = a match {
    case Some(x) => Ready(x)
    case None    => Empty
  }

  /**
    * Monad type class for `Pot`
    */
  implicit object potMonad extends Monad[Pot] {
    override def map[A, B](fa: Pot[A])(f: A => B): Pot[B] =
      fa.map(f)

    override def flatMap[A, B](fa: Pot[A])(f: A => Pot[B]): Pot[B] =
      fa.flatMap(f)

    override def isEqual[A](fa1: Pot[A], fa2: Pot[A])(eqF: (A, A) => Boolean): Boolean = {
      if (fa1.nonEmpty && fa2.nonEmpty)
        eqF(fa1.get, fa2.get)
      else fa1 == fa2
    }
  }

  /** Default value for startTime. */
  protected[data] def currentTime = new Date().getTime

}

case object Empty extends Pot[Nothing] {
  def get           = throw new NoSuchElementException("Empty.get")
  def isEmpty       = true
  def isPending     = false
  def isFailed      = false
  def isStale       = false
  def isUnavailable = false
  def retriesLeft   = 0
  def state         = PotState.PotEmpty
  def retryPolicy   = Retry.None

  override def pending(startTime: Long = Pot.currentTime) = Pending(startTime)
  override def fail(exception: Throwable)                 = Failed(exception)
}

case object Unavailable extends Pot[Nothing] {
  def get           = throw new NoSuchElementException("Unavailable.get")
  def isEmpty       = true
  def isPending     = false
  def isFailed      = true
  def isStale       = false
  def isUnavailable = true
  def retriesLeft   = 0
  def state         = PotState.PotUnavailable
  def retryPolicy   = Retry.None

  override def pending(startTime: Long = Pot.currentTime) = Pending(startTime)
  override def fail(exception: Throwable)                 = Failed(exception)
}

final case class Ready[+A](x: A) extends Pot[A] {
  def get           = x
  def isEmpty       = false
  def isPending     = false
  def isFailed      = false
  def isStale       = false
  def isUnavailable = false
  def retriesLeft   = 0
  def state         = PotState.PotReady
  def retryPolicy   = Retry.None

  override def pending(startTime: Long = Pot.currentTime) = PendingStale(x, startTime)
  override def fail(exception: Throwable)                 = FailedStale(x, exception)
}

sealed trait PendingBase {
  def startTime: Long
  def isPending                                     = true
  def isUnavailable                                 = false
  def state                                         = PotState.PotPending
  def duration(currentTime: Long = Pot.currentTime) = (currentTime - startTime).toInt
}

final case class Pending(startTime: Long = Pot.currentTime) extends Pot[Nothing] with PendingBase {
  def get      = throw new NoSuchElementException("Pending.get")
  def isEmpty  = true
  def isFailed = false
  def isStale  = false

  override def pending(startTime: Long = startTime) = copy(startTime)
  override def fail(exception: Throwable)           = Failed(exception)
}

final case class PendingStale[+A](x: A, startTime: Long = Pot.currentTime) extends Pot[A] with PendingBase {
  def get      = x
  def isEmpty  = false
  def isFailed = false
  def isStale  = true

  override def pending(startTime: Long = startTime) = copy(x, startTime)
  override def fail(exception: Throwable)           = FailedStale(x, exception)
}

sealed trait FailedBase {
  def exception: Throwable
  def isPending     = false
  def isFailed      = true
  def isUnavailable = false
  def state         = PotState.PotFailed
}

final case class Failed(exception: Throwable) extends Pot[Nothing] with FailedBase {
  def get                      = throw new NoSuchElementException("Failed.get")
  def isEmpty                  = true
  def isStale                  = false
  override def exceptionOption = Some(exception)

  override def recoverWith[B](f: PartialFunction[Throwable, Pot[B]]): Pot[B] = {
    if (f isDefinedAt exception)
      f(exception)
    else
      this
  }

  override def recover[B](f: PartialFunction[Throwable, B]): Pot[B] = this

  override def pending(startTime: Long = Pot.currentTime) = Pending(startTime)
  override def fail(exception: Throwable)                 = Failed(exception)
}

final case class FailedStale[+A](x: A, exception: Throwable) extends Pot[A] with FailedBase {
  def get                      = x
  def isEmpty                  = false
  def isStale                  = true
  override def exceptionOption = Some(exception)

  override def recoverWith[B >: A](f: PartialFunction[Throwable, Pot[B]]): Pot[B] = {
    if (f isDefinedAt exception)
      f(exception)
    else
      this
  }

  override def recover[B >: A](f: PartialFunction[Throwable, B]): Pot[B] = this

  override def pending(startTime: Long = Pot.currentTime) = PendingStale(x, startTime)
  override def fail(exception: Throwable)                 = FailedStale(x, exception)
}
