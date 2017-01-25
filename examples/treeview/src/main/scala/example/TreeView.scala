package example

import diode._
import scalatags.JsDom.all._

class TreeView(root: ModelRO[FileNode], parent: Seq[String], selection: ModelRO[Seq[String]], dispatcher: Dispatcher) {
  val id       = root().id
  val path     = parent :+ id
  val childSeq = build

  // recursively build the tree view
  def build = {
    root().children.zipWithIndex.map {
      case (c, idx) =>
        new TreeView(root.zoom(_.children(idx)), path, selection, dispatcher)
    }
  }

  def render: Frag = {
    val isSelected = if (selection().nonEmpty && selection().last == id) "active" else ""

    def renderName(name: String) =
      a(href := "#", cls := isSelected, onclick := { () =>
        dispatcher(Select(path))
      }, name)

    root() match {
      case Directory(id, name, children) =>
        li(cls := s"directory", renderName(name), ul(childSeq.map(_.render)))
      case File(id, name) =>
        li(cls := s"file", renderName(name))
    }
  }
}
