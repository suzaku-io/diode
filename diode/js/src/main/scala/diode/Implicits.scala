package diode

import diode.util._

object Implicits {
  implicit lazy val runAfter: RunAfter = new RunAfterJS
}
