package diode

import diode.util.RunAfterJVM
import diode.util.RunAfter

object Implicits {
  implicit object runAfterImpl extends RunAfterJVM
}
