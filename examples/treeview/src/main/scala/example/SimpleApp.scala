package example

import java.util.UUID

import scala.scalajs.js.JSApp
import scala.scalajs.js.annotation.JSExport
import scalatags.JsDom.all._
import org.scalajs.dom

@JSExport("SimpleApp")
object SimpleApp extends JSApp {
  // initial data
  val data = Directory("/", "/", Vector(
    Directory("2", "My files", Vector(
      Directory("3", "Documents", Vector(
        File("F3", "HaukiOnKala.doc")
      ))
    )), File("F1", "boot.sys")
  ))

  var id = 0
  def nextId = {
    id += 1
    id
  }

  @JSExport
  override def main(): Unit = {
    val root = dom.document.getElementById("root")
    // subscribe to changes in the application model and call render when anything changes
    AppModel.subscribe(() => render(root))
    // start the application by dispatching a ReplaceTree action
    AppModel.dispatch(ReplaceTree(data))
  }

  def render(root: dom.Element) = {
    // rebuild the tree view
    val selectionLoc = AppModel.zoom(_.tree.selected).value
    val treeView = new TreeView(AppModel.zoom(_.tree.root), Seq.empty, selectionLoc, AppModel)

    def renderButtons(selected: Boolean) = {
      div(
        button(if(!selected) disabled else "", cls := "btn",
          onclick := { () => AppModel(AddNode(selectionLoc, Directory(UUID.randomUUID().toString, s"New directory $nextId"))) },
          "Create dir"),
        button(if(!selected) disabled else "", cls := "btn",
          onclick := { () => AppModel(AddNode(selectionLoc, File(UUID.randomUUID().toString, s"New file $nextId"))) },
          "Create file"),
        button(if(!selected) disabled else "", cls := "btn",
          onclick := { () => AppModel(RemoveNode(selectionLoc)) },
          "Remove")
      )
    }

    val e = div(
      h1("Diode example"),
      renderButtons(selectionLoc.nonEmpty),
      treeView.render
    ).render
    // clear and update contents
    root.innerHTML = ""
    root.appendChild(e)
  }
}
