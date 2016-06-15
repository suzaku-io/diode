package example

import java.util.UUID

import diode.Action

// Define our application model
case class AppModel(todos: Todos)

case class TodoId(id: UUID)

object TodoId {
  def random = new TodoId(UUID.randomUUID)
}

case class Todos(todoList: Seq[Todo])

case class Todo(id: TodoId, title: String, isCompleted: Boolean)

sealed abstract class TodoFilter(val link: String, val title: String, val accepts: Todo => Boolean)

object TodoFilter {

  object All extends TodoFilter("", "All", _ => true)

  object Active extends TodoFilter("active", "Active", !_.isCompleted)

  object Completed extends TodoFilter("completed", "Completed", _.isCompleted)

  val values = List[TodoFilter](All, Active, Completed)
}

// define actions
case object InitTodos extends Action

case class AddTodo(title: String) extends Action

case class ToggleAll(checked: Boolean) extends Action

case class ToggleCompleted(id: TodoId) extends Action

case class Update(id: TodoId, title: String) extends Action

case class Delete(id: TodoId) extends Action

case class SelectFilter(filter: TodoFilter) extends Action

case object ClearCompleted extends Action
