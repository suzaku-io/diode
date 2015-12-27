package diode

import diode.data._

import scala.language.higherKinds

/**
  * Defines a Diode specific Functor for traversing models with `Option`s etc.
  */
trait Functor[F[_]] {
  /**
    * Maps a functor value to another value using function `f`
    */
  def map[A, B](fa: F[A])(f: A => B): F[B]

  /**
    * Maps a functor value to another functor using function `f`
    */
  def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]

  /**
    * Checks if the contents of two functors are equal, applying function `f` to the first value
    */
  def isEqual[B](fa1: F[B], fa2: F[B])(eqF: (B, B) => Boolean): Boolean
}

object Functor {

  implicit object optionFunctor extends Functor[Option] {
    override def map[A, B](fa: Option[A])(f: A => B): Option[B] =
      fa.map(f)

    override def flatMap[A, B](fa: Option[A])(f: A => Option[B]): Option[B] =
      fa.flatMap(f)

    override def isEqual[B](fa1: Option[B], fa2: Option[B])(eqF: (B, B) => Boolean): Boolean = {
      (fa1, fa2) match {
        case (Some(a1), Some(a2)) => eqF(a1, a2)
        case (None, None) => true
        case _ => false
      }
    }
  }

  implicit object potFunctor extends Functor[Pot] {
    override def map[A, B](fa: Pot[A])(f: A => B): Pot[B] =
      fa.map(f)

    override def flatMap[A, B](fa: Pot[A])(f: A => Pot[B]): Pot[B] =
      fa.flatMap(f)

    override def isEqual[B](fa1: Pot[B], fa2: Pot[B])(eqF: (B, B) => Boolean): Boolean = {
      if (fa1.nonEmpty && fa2.nonEmpty)
        eqF(fa1.get, fa2.get)
      else if (fa1 == fa2)
        true
      else
        false
    }
  }

}
