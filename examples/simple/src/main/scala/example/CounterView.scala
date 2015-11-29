package example

import diode._
import scalatags.JsDom.all._

/**
  * Counter view renders the counter value and provides interaction through
  * various buttons affecting the counter value.
  *
  * @param counter Model reader for the counter value
  * @param dispatch Dispatcher
  */
class CounterView(counter: ModelR[Int], dispatch: Dispatcher) {
  def render = {
    div(
      h3("Counter"),
      p("Value = ", b(counter())),
      button(onclick := { () => dispatch(Increase(2)) }, "Increase"),
      button(onclick := { () => dispatch(Decrease(1)) }, "Decrease"),
      button(onclick := { () => dispatch(Reset) }, "Reset")
    )
  }
}
