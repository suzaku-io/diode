package example

import diode.ActionResult.ModelUpdate
import diode.RootModelRW
import utest._

object DirectoryTreeHandlerTests extends TestSuite {
  def tests = TestSuite {
    // test data
    val dir = Directory("/", "/", Vector(
      Directory("2", "My files", Vector(
        Directory("3", "Documents", Vector(
          File("F3", "HaukiOnKala.doc")
        ))
      )), File("F1", "boot.sys")
    ))
    val dir2 = Directory("/", "/")

    def build = new DirectoryTreeHandler(new RootModelRW(dir))

    'ReplaceTree - {
      val handler = build
      val result = handler.handle(ReplaceTree(dir2))
      assert(result == ModelUpdate(dir2))
    }

    'AddNode - {
      val handler = build
      val result = handler.handle(AddNode(Seq("/", "2"), File("new", "new")))
      assert(result == ModelUpdate(Directory("/", "/", Vector(
        Directory("2", "My files", Vector(
          Directory("3", "Documents", Vector(
            File("F3", "HaukiOnKala.doc")
          )),
          File("new", "new")
        )), File("F1", "boot.sys")
      ))
      ))
    }

    'RemoveNode - {
      val handler = build
      val result = handler.handle(RemoveNode(Seq("/", "2")))
      assertMatch(result) {
        case ModelUpdate(Directory("/", "/", Vector(File("F1", "boot.sys")))) =>
      }
    }

    'ReplaceNode - {
      val handler = build
      val result = handler.handle(ReplaceNode(Seq("/", "F1"), File("F1", "bootRenamed.sys")))
      assertMatch(result) {
        case ModelUpdate(m) => m == Directory("/", "/", Vector(
          Directory("2", "My files", Vector(
            Directory("3", "Documents", Vector(
              File("F3", "HaukiOnKala.doc")
            ))
          )), File("F1", "bootRenamed.sys")
        ))
      }
    }
  }
}
