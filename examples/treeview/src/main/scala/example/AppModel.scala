package example

import diode.ActionResult.ModelUpdate
import diode._

// define a recursive file/directory structure
sealed trait FileNode {
  def id: String
  def name: String
  def children: IndexedSeq[FileNode]
}

final case class Directory(id: String, name: String, children: IndexedSeq[FileNode] = IndexedSeq.empty) extends FileNode {
}

final case class File(id: String, name: String) extends FileNode {
  val children = IndexedSeq.empty[FileNode]
}

case class Tree(root: Directory, selected: Seq[String])

// Define the root of our application model
case class RootModel(tree: Tree)

// Define actions
case class ReplaceTree(newTree: Directory)

// location is defined by a sequence of identifiers
case class AddNode(loc: Seq[String], node: FileNode)

case class RemoveNode(loc: Seq[String])

case class ReplaceNode(loc: Seq[String], node: FileNode)

case class Select(selected: Seq[String])

/**
  * AppModel provides the actual instance of the `RootModel` and all the action
  * handlers we need. Everything else comes from the `Circuit`
  */
object AppModel extends Circuit[RootModel] {
  // define initial value for the application model
  var model = RootModel(Tree(Directory("", "", Vector.empty), Seq.empty))

  // zoom into the model, providing access only to the `root` directory of the tree
  val treeHandler = new ActionHandler(
    zoomRW(_.tree)((m, v) => m.copy(tree = v))
      .zoomRW(_.root)((m, v) => m.copy(root = v))) {

    /**
      * Helper function to zoom into directory hierarchy, delivering the `children` sequence of the last directory.
      *
      * @param loc Sequence of directory identifiers
      * @param rw Reader/Writer for current directory
      * @return
      * `Some(childrenRW)` if the directory was found or
      * `None` if something went wrong
      */
    def zoomToChildren[M](loc: Seq[String], rw: ModelRW[M, Directory]): Option[ModelRW[M, IndexedSeq[FileNode]]] = {
      if (loc.isEmpty) {
        Some(rw.zoomRW(_.children)((m, v) => m.copy(children = v)))
      } else {
        // find the index for the next location in the chain and make sure it's a directory
        rw.value.children.indexWhere(n => n.id == loc.head && n.isInstanceOf[Directory]) match {
          case -1 =>
            // should not happen!
            None
          case idx =>
            // zoom into the directory position given by `idx` and continue recursion
            zoomToChildren(loc.tail, rw.zoomRW(_.children(idx).asInstanceOf[Directory])((m, v) =>
              m.copy(children = (m.children.take(idx) :+ v) ++ m.children.drop(idx + 1))
            ))
        }
      }
    }

    override def handle = {
      case ReplaceTree(newTree) =>
        update(newTree)
      case AddNode(parent, node) =>
        println(s"Adding to $parent")
        // zoom to parent directory and add new node at the end of its children list
        zoomToChildren(parent.tail, modelRW) match {
          case Some(rw) => ModelUpdate(rw.update(rw.value :+ node))
          case None => noChange
        }
      case RemoveNode(loc) =>
        println(s"Removing $loc")
        // zoom to parent directory and remove node from its children list
        val nodeId = loc.last
        if (loc.init.nonEmpty) {
          zoomToChildren(loc.init.tail, modelRW) match {
            case Some(rw) => ModelUpdate(rw.update(rw.value.filterNot(_.id == nodeId)))
            case None => noChange
          }
        } else {
          // cannot remove root
          noChange
        }
      case ReplaceNode(loc, node) =>
        println(s"Replacing $loc")
        // zoom to parent directory and replace node in its children list with a new one
        val nodeId = loc.last
        if (loc.init.nonEmpty) {
          zoomToChildren(loc.init.tail, modelRW) match {
            case Some(rw) => ModelUpdate(rw.update(rw.value.map(n => if (n.id == nodeId) node else n)))
            case None => noChange
          }
        } else {
          // cannot replace root
          noChange
        }
    }
  }
  val selectionHandler = new ActionHandler(
    zoomRW(_.tree)((m, v) => m.copy(tree = v))
      .zoomRW(_.selected)((m, v) => m.copy(selected = v))) {
    override def handle = {
      case Select(sel) => update(sel)
    }
  }

  val actionHandler = combineHandlers(treeHandler, selectionHandler)
}
