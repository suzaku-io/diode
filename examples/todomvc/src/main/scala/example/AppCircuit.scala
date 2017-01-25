package example

import diode._
import diode.react.ReactConnector

/**
  * AppCircuit provides the actual instance of the `AppModel` and all the action
  * handlers we need. Everything else comes from the `Circuit`
  */
object AppCircuit extends Circuit[AppModel] with ReactConnector[AppModel] {
  // define initial value for the application model
  def initialModel = AppModel(Todos(Seq()))

  override val actionHandler = composeHandlers(
    new TodoHandler(zoomTo(_.todos.todoList))
  )
}

class TodoHandler[M](modelRW: ModelRW[M, Seq[Todo]]) extends ActionHandler(modelRW) {

  def updateOne(Id: TodoId)(f: Todo => Todo): Seq[Todo] =
    value.map {
      case found @ Todo(Id, _, _) => f(found)
      case other                  => other
    }

  override def handle = {
    case InitTodos =>
      println("Initializing todos")
      updated(List(Todo(TodoId.random, "Test your code!", false)))
    case AddTodo(title) =>
      updated(value :+ Todo(TodoId.random, title, false))
    case ToggleAll(checked) =>
      updated(value.map(_.copy(isCompleted = checked)))
    case ToggleCompleted(id) =>
      updated(updateOne(id)(old => old.copy(isCompleted = !old.isCompleted)))
    case Update(id, title) =>
      updated(updateOne(id)(_.copy(title = title)))
    case Delete(id) =>
      updated(value.filterNot(_.id == id))
    case ClearCompleted =>
      updated(value.filterNot(_.isCompleted))
  }
}
