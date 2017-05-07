package example

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._

object Footer {

  case class Props(
      filterLink: TodoFilter => VdomTag,
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
        ^.visibility.hidden.when(p.completedCount == 0)
      )

    def filterLink(p: Props)(s: TodoFilter) =
      <.li(p.filterLink(s)((^.className := "selected").when(p.currentFilter == s), s.title))

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
          TodoFilter.values.toTagMod(filterLink(p)(_))
        ),
        clearButton(p)
      )
  }

  private val component = ScalaComponent
    .builder[Props]("Footer")
    .stateless
    .renderBackend[Backend]
    .build

  def apply(p: Props) = component(p)
}
