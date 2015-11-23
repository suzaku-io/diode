package example

import diode._
import scalatags.JsDom.all._

class TreeView(root: ModelR[FileNode], parent: Seq[String], selection: ModelR[Seq[String]], dispatcher: Dispatcher) {
  val id = root.value.id
  val path = parent :+ id
  val childSeq = build

  // recursively build the tree view
  def build = {
    root.value.children.zipWithIndex.map { case (c, idx) =>
      new TreeView(root.zoom(_.children(idx)), path, selection, dispatcher)
    }
  }

  def render: Frag = {
    val isSelected = if(selection.value.nonEmpty && selection.value.last == id) "active" else ""

    def renderName(name: String) =
      a(href := "#", cls := isSelected, onclick := {() => dispatcher(Select(path))}, name)

    root.value match {
      case Directory(id, name, children) =>
        li(cls := s"directory", renderName(name),
          ul(childSeq.map(_.render))
        )
      case File(id, name) =>
        li(cls := s"file", renderName(name))
    }
  }
}
