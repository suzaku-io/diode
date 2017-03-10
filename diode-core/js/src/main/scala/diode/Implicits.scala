package diode

import diode.util.RunAfterJS

object Implicits {
  implicit object runAfterImpl extends RunAfterJS
}
