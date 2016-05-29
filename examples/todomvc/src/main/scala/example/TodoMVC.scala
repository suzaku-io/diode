package example

import boopickle.Default._
import diode.dev.{Hooks, PersistStateIDB}
import org.scalajs.dom

import scala.scalajs.js.JSApp
import scala.scalajs.js.annotation.JSExport
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router._

import scala.scalajs.js.typedarray.TypedArrayBufferOps._
import scala.scalajs.js.typedarray._

@JSExport("TodoMVC")
object TodoMVC extends JSApp {

  val baseUrl = BaseUrl(dom.window.location.href.takeWhile(_ != '#'))

  val routerConfig: RouterConfig[TodoFilter] = RouterConfigDsl[TodoFilter].buildConfig { dsl =>
    import dsl._

    val todoConnection = AppCircuit.connect(_.todos)

    /* how the application renders the list given a filter */
    def filterRoute(s: TodoFilter): Rule = staticRoute("#/" + s.link, s) ~> renderR(router => todoConnection(p => TodoList(p, s, router)))

    val filterRoutes: Rule = TodoFilter.values.map(filterRoute).reduce(_ | _)

    /* build a final RouterConfig with a default page */
    filterRoutes.notFound(redirectToPage(TodoFilter.All)(Redirect.Replace))
  }

  /** The router is itself a React component, which at this point is not mounted (U-suffix) */
  val router: ReactComponentU[Unit, Resolution[TodoFilter], Any, TopNode] =
    Router(baseUrl, routerConfig.logToConsole)()

  /**
    * Function to pickle application model into a TypedArray
    *
    * @param model
    * @return
    */
  def pickle(model: AppModel) = {
    val data = Pickle.intoBytes(model)
    data.typedArray().subarray(data.position, data.limit)
  }

  /**
    * Function to unpickle application model from a TypedArray
    *
    * @param data
    * @return
    */
  def unpickle(data: Int8Array) = {
    Unpickle[AppModel].fromBytes(TypedArrayBuffer.wrap(data))
  }

  @JSExport
  override def main(): Unit = {
    // add a development tool to persist application state
    AppCircuit.addProcessor(new PersistStateIDB(pickle, unpickle))
    // hook it into Ctrl+Shift+S and Ctrl+Shift+L
    Hooks.hookPersistState("test", AppCircuit)

    AppCircuit.dispatch(InitTodos)
    ReactDOM.render(router, dom.document.getElementsByClassName("todoapp")(0))
  }
}
