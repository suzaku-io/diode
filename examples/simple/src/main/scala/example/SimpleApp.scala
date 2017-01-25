package example

import scala.scalajs.js.JSApp
import scala.scalajs.js.annotation.JSExport
import scalatags.JsDom.all._
import org.scalajs.dom._

@JSExport("SimpleApp")
object SimpleApp extends JSApp {
  // create a view for the counter
  val counter = new CounterView(AppCircuit.zoom(_.counter), AppCircuit)

  @JSExport
  override def main(): Unit = {
    val root = document.getElementById("root")
    // subscribe to changes in the application model and call render when anything changes
    AppCircuit.subscribe(AppCircuit.zoom(identity))(_ => render(root))
    // start the application by dispatching a Reset action
    AppCircuit(Reset)
  }

  def render(root: Element) = {
    val e = div(
      cls := "container",
      div(img(src := "diode-logo-small.png")),
      h1("Simple counter example"),
      p(a(href := "https://github.com/suzaku-io/diode/tree/master/examples/simple", "Source code")),
      counter.render // renders the counter view
    ).render
    // clear and update contents
    root.innerHTML = ""
    root.appendChild(e)
  }
}
