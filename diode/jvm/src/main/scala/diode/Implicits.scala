package diode

import diode.util.RunAfterJVM
import diode.util.RunAfter

object Implicits {
  implicit lazy val runAfter: RunAfter = new RunAfterJVM
}
