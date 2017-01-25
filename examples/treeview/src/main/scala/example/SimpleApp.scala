package example

import java.util.UUID

import diode._

import scala.scalajs.js.JSApp
import scala.scalajs.js.annotation.JSExport
import scalatags.JsDom.all._
import org.scalajs.dom

@JSExport("SimpleApp")
object SimpleApp extends JSApp {
  // initial data
  val data = Directory("/",
                       "/",
                       Vector(
                         Directory("2",
                                   "My files",
                                   Vector(
                                     Directory("3",
                                               "Documents",
                                               Vector(
                                                 File("F3", "HaukiOnKala.doc")
                                               ))
                                   )),
                         File("F1", "boot.sys")
                       ))

  var id = 0
  def nextId = {
    id += 1
    id
  }

  var currentModel: FileNode = AppCircuit.zoom(_.tree.root).value
  var treeView               = new TreeView(AppCircuit.zoom(_.tree.root), Seq.empty, AppCircuit.zoom(_.tree.selected), AppCircuit)

  @JSExport
  override def main(): Unit = {
    val root = dom.document.getElementById("root")
    val tree = AppCircuit.zoom(_.tree)
    // subscribe to changes in the application model and call render when anything changes
    AppCircuit.subscribe(tree)(_ => render(root, tree))
    // start the application by dispatching a ReplaceTree action
    AppCircuit.dispatch(ReplaceTree(data))
  }

  def render(root: dom.Element, tree: ModelRO[Tree]) = {
    // rebuild the tree view if the model has changed
    val dirRoot: ModelRO[FileNode] = tree.zoom(_.root)
    if (dirRoot =!= currentModel) {
      currentModel = dirRoot.value
      treeView = new TreeView(dirRoot, Seq.empty, tree.zoom(_.selected), AppCircuit)
    }

    val selectionLoc = tree().selected
    def renderButtons(selected: Boolean) = {
      div(
        button(
          if (!selected) disabled else "",
          cls := "btn",
          onclick := { () =>
            AppCircuit(AddNode(selectionLoc, Directory(UUID.randomUUID().toString, s"New directory $nextId")))
          },
          "Create dir"
        ),
        button(
          if (!selected) disabled else "",
          cls := "btn",
          onclick := { () =>
            AppCircuit(AddNode(selectionLoc, File(UUID.randomUUID().toString, s"New file $nextId")))
          },
          "Create file"
        ),
        button(if (!selected) disabled else "", cls := "btn", onclick := { () =>
          AppCircuit(RemoveNode(selectionLoc))
        }, "Remove")
      )
    }

    val e = div(
      cls := "container",
      div(img(src := "diode-logo-small.png")),
      h1("Treeview example"),
      p(a(href := "https://github.com/suzaku-io/diode/tree/master/examples/treeview", "Source code")),
      renderButtons(selectionLoc.nonEmpty),
      treeView.render
    ).render
    // clear and update contents
    root.innerHTML = ""
    root.appendChild(e)
  }
}
