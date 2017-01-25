package example

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.prefix_<^._
import org.scalajs.dom.ext.KeyCode

object TodoView {

  case class Props(onToggle: Callback,
                   onDelete: Callback,
                   onStartEditing: Callback,
                   onUpdateTitle: String => Callback,
                   onCancelEditing: Callback,
                   todo: Todo,
                   isEditing: Boolean)

  case class State(editText: String)

  class Backend($ : BackendScope[Props, State]) {
    val x = $.props.map(_.isEditing)
    def editFieldSubmit(p: Props): Callback =
      $.state.flatMap(
        s =>
          if (s.editText.trim == "")
            p.onDelete
          else p.onUpdateTitle(s.editText.trim))

    def resetText(p: Props): Callback =
      $.modState(_.copy(editText = p.todo.title))

    def editFieldKeyDown(p: Props): ReactKeyboardEvent => Option[Callback] =
      e =>
        e.nativeEvent.keyCode match {
          case KeyCode.Escape => Some(resetText(p) >> p.onCancelEditing)
          case KeyCode.Enter  => Some(editFieldSubmit(p))
          case _              => None
      }

    val editFieldChanged: ReactEventI => Callback =
      e => Callback { e.persist() } >> $.modState(_.copy(editText = e.target.value))

    def render(p: Props, s: State): ReactElement = {
      <.li(
        ^.classSet(
          "completed" -> p.todo.isCompleted,
          "editing"   -> p.isEditing
        ),
        <.div(
          ^.className := "view",
          <.input.checkbox(
            ^.className := "toggle",
            ^.checked := p.todo.isCompleted,
            ^.onChange --> p.onToggle
          ),
          <.label(
            p.todo.title,
            ^.onDoubleClick --> p.onStartEditing
          ),
          <.button(
            ^.className := "destroy",
            ^.onClick --> p.onDelete
          )
        ),
        <.input(
          ^.className := "edit",
          ^.onBlur --> editFieldSubmit(p),
          ^.onChange ==> editFieldChanged,
          ^.onKeyDown ==>? editFieldKeyDown(p),
          ^.value := s.editText
        )
      )
    }
  }

  val component = ReactComponentB[Props]("CTodoItem")
    .initialState_P(p => State(p.todo.title))
    .renderBackend[Backend]
    .build

  def apply(P: Props) =
    component.withKey(P.todo.id.id.toString)(P)
}
