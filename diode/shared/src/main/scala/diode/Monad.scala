package diode

import diode.data._

import scala.language.higherKinds

/**
  * Defines a Diode specific Monad for traversing models with `Option`s etc.
  */
trait Monad[F[_]] {
  /**
    * Maps a monad value to another value using function `f`
    */
  def map[A, B](fa: F[A])(f: A => B): F[B]

  /**
    * Maps a monad value to another monad using function `f`
    */
  def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]

  /**
    * Checks if the contents of two monads are equal
    */
  def isEqual[A](fa1: F[A], fa2: F[A])(eqF: (A, A) => Boolean): Boolean
}

object Monad {

  /**
    * Monad type class for `Option`
    */
  implicit object optionMonad extends Monad[Option] {
    override def map[A, B](fa: Option[A])(f: A => B): Option[B] =
      fa.map(f)

    override def flatMap[A, B](fa: Option[A])(f: A => Option[B]): Option[B] =
      fa.flatMap(f)

    override def isEqual[A](fa1: Option[A], fa2: Option[A])(eqF: (A, A) => Boolean): Boolean = {
      (fa1, fa2) match {
        case (Some(a1), Some(a2)) => eqF(a1, a2)
        case (None, None) => true
        case _ => false
      }
    }
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
      else if (fa1 == fa2)
        true
      else
        false
    }
  }

}
