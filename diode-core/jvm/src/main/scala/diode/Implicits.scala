package diode

import diode.util.RunAfterJVM

object Implicits {
  implicit object runAfterImpl extends RunAfterJVM
}
