package example

import diode.react.ModelProxy
import diode.Action
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router._
import japgolly.scalajs.react.vdom.prefix_<^._
import org.scalajs.dom.ext.KeyCode

object TodoList {

  case class Props(proxy: ModelProxy[Todos], currentFilter: TodoFilter, ctl: RouterCtl[TodoFilter])

  case class State(editing: Option[TodoId])

  class Backend($ : BackendScope[Props, State]) {
    def mounted(props: Props) = Callback {}

    def handleNewTodoKeyDown(dispatch: Action => Callback)(e: ReactKeyboardEventI): Option[Callback] = {
      val title = e.target.value.trim
      if (e.nativeEvent.keyCode == KeyCode.Enter && title.nonEmpty) {
        Some(Callback(e.target.value = "") >> dispatch(AddTodo(title)))
      } else {
        None
      }
    }

    def editingDone(): Callback =
      $.modState(_.copy(editing = None))

    val startEditing: TodoId => Callback =
      id => $.modState(_.copy(editing = Some(id)))

    def render(p: Props, s: State) = {
      val proxy                        = p.proxy()
      val dispatch: Action => Callback = p.proxy.dispatchCB
      val todos                        = proxy.todoList
      val filteredTodos                = todos filter p.currentFilter.accepts
      val activeCount                  = todos count TodoFilter.Active.accepts
      val completedCount               = todos.length - activeCount

      <.div(
        <.h1("todos"),
        <.header(
          ^.className := "header",
          <.input(
            ^.className := "new-todo",
            ^.placeholder := "What needs to be done?",
            ^.onKeyDown ==>? handleNewTodoKeyDown(dispatch),
            ^.autoFocus := true
          )
        ),
        todos.nonEmpty ?= todoList(dispatch, s.editing, filteredTodos, activeCount),
        todos.nonEmpty ?= footer(p, dispatch, p.currentFilter, activeCount, completedCount)
      )
    }

    def todoList(dispatch: Action => Callback, editing: Option[TodoId], todos: Seq[Todo], activeCount: Int) =
      <.section(
        ^.className := "main",
        <.input.checkbox(
          ^.className := "toggle-all",
          ^.checked := activeCount == 0,
          ^.onChange ==> { e: ReactEventI =>
            dispatch(ToggleAll(e.target.checked))
          }
        ),
        <.ul(
          ^.className := "todo-list",
          todos.map(
            todo =>
              TodoView(TodoView.Props(
                onToggle = dispatch(ToggleCompleted(todo.id)),
                onDelete = dispatch(Delete(todo.id)),
                onStartEditing = startEditing(todo.id),
                onUpdateTitle = title => dispatch(Update(todo.id, title)) >> editingDone(),
                onCancelEditing = editingDone(),
                todo = todo,
                isEditing = editing.contains(todo.id)
              )))
        )
      )

    def footer(p: Props, dispatch: Action => Callback, currentFilter: TodoFilter, activeCount: Int, completedCount: Int): ReactElement =
      Footer(
        Footer.Props(
          filterLink = p.ctl.link,
          onSelectFilter = f => dispatch(SelectFilter(f)),
          onClearCompleted = dispatch(ClearCompleted),
          currentFilter = currentFilter,
          activeCount = activeCount,
          completedCount = completedCount
        ))
  }

  private val component = ReactComponentB[Props]("TodoList")
    .initialState_P(p => State(None))
    .renderBackend[Backend]
    .componentDidMount(scope => scope.backend.mounted(scope.props))
    .build

  def apply(proxy: ModelProxy[Todos], currentFilter: TodoFilter, ctl: RouterCtl[TodoFilter]) = component(Props(proxy, currentFilter, ctl))
}
