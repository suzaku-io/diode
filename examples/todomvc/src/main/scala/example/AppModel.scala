package example

import java.util.UUID

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

case class AddTodo(title: String)

case class ToggleAll(checked: Boolean)

case class ToggleCompleted(id: TodoId)

case class Update(id: TodoId, title: String)

case class Delete(id: TodoId)

case class SelectFilter(filter: TodoFilter)

case object ClearCompleted
