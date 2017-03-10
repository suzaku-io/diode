package example

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.prefix_<^._

object Footer {

  case class Props(
      filterLink: TodoFilter => ReactTag,
      onSelectFilter: TodoFilter => Callback,
      onClearCompleted: Callback,
      currentFilter: TodoFilter,
      activeCount: Int,
      completedCount: Int
  )

  class Backend($ : BackendScope[Props, Unit]) {
    def clearButton(p: Props) =
      <.button(
        ^.className := "clear-completed",
        ^.onClick --> p.onClearCompleted,
        "Clear completed",
        (p.completedCount == 0) ?= ^.visibility.hidden
      )

    def filterLink(p: Props)(s: TodoFilter) =
      <.li(p.filterLink(s)((p.currentFilter == s) ?= (^.className := "selected"), s.title))

    def render(p: Props) =
      <.footer(
        ^.className := "footer",
        <.span(
          ^.className := "todo-count",
          <.strong(p.activeCount),
          s" ${if (p.activeCount == 1) "item" else "items"} left"
        ),
        <.ul(
          ^.className := "filters",
          TodoFilter.values.map(filterLink(p)(_))
        ),
        clearButton(p)
      )
  }

  private val component = ReactComponentB[Props]("Footer").stateless
    .renderBackend[Backend]
    .build

  def apply(p: Props) = component(p)
}
