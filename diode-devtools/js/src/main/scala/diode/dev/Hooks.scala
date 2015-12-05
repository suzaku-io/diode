package diode.dev

import diode.Dispatcher
import org.scalajs.dom
import org.scalajs.dom.raw.KeyboardEvent

object Hooks {
  def hookPersistState(id: String, dispatch: Dispatcher) = {
    def keyDown(event: KeyboardEvent) = {
      if (event.ctrlKey && event.shiftKey) {
        val c = Character.toChars(event.keyCode)(0).toLower
        c match {
          case 's' =>
            event.preventDefault()
            dispatch(PersistState.Save(id))
          case 'l' =>
            event.preventDefault()
            dispatch(PersistState.Load(id))
          case _ =>
        }
      }
    }
    dom.window.addEventListener("keydown", keyDown _)
  }
}
