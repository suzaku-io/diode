package example

import scala.scalajs.js.JSApp
import scala.scalajs.js.annotation.JSExport
import scalatags.JsDom.all._
import org.scalajs.dom

@JSExport("SimpleApp")
object SimpleApp extends JSApp {
  @JSExport
  override def main(): Unit = {
    val root = dom.document.getElementById("root")
    // connect the RAF batcher
    AppCircuit.addProcessor(new RAFBatcher)
    // subscribe to changes in the animations
    AppCircuit.subscribe(AppCircuit.zoom(_.animations))(reader => renderAnimations(reader()))
    // first render
    render(root)
  }

  // Animation generators
  val animGenerators: Seq[(String, Double => Animation)] = Seq(
    "Circle" -> (r => Circle(r * 120 + 30)),
    "Spiral" -> (r => Spiral(r * 120 + 30)),
    "Flower" -> (r => Flower(r * 60 + 30))
  )

  // store previous state of animations so we can render only when something changes
  var prevAnimations = Map.empty[Int, Animated]

  def clearElement(elem: dom.Element): dom.Element = {
    while (elem.hasChildNodes) elem.removeChild(elem.firstChild)
    elem
  }

  def renderAnimation(animId: Int, animated: Animated, prevAnim: Option[Animated]) = {
    import scalatags.JsDom.{svgTags => svg, svgAttrs => svga}
    // only render if animation has changed
    if (!prevAnim.exists(_.isRunning == animated.isRunning)) {
      val elem = clearElement(dom.document.querySelector(s"#anim-$animId .anim-buttons"))
      elem.appendChild(
        div(
          cls := "btn-group",
          if (animated.isRunning)
            button(cls := "btn btn-warning", "Pause", onclick := { () =>
              AppCircuit.dispatch(PauseAnimation(animId))
            })
          else
            button(cls := "btn btn-success", "Continue", onclick := { () =>
              AppCircuit.dispatch(ContinueAnimation(animId))
            }),
          button(cls := "btn btn-danger", "Delete", onclick := { () =>
            AppCircuit.dispatch(DeleteAnimation(animId))
          })
        ).render)
    }
    if (!prevAnim.exists(_.animation eq animated.animation)) {
      val animation = animated.animation
      val elem      = clearElement(dom.document.querySelector(s"#anim-$animId .anim-area"))
      elem.appendChild(
        svg
          .svg(
            svga.width := 300,
            svga.height := 300,
            svg.circle(
              svga.cx := animation.position.x * 130 + 150,
              svga.cy := animation.position.y * 130 + 150,
              svga.r := animation.scale * 20,
              svga.fill := animation.color
            )
          )
          .render)
    }
  }

  def renderAnimations(animations: Map[Int, Animated]) = {
    if (animations.size != prevAnimations.size) {
      // number of animations has changed, render everything
      prevAnimations = Map()
      val elem = div(
        animations.toSeq.sortBy(_._1).map {
          case (animId, Animated(_, animation, isRunning, _)) =>
            div(cls := "row", id := s"anim-$animId", div(cls := "anim-buttons"), div(cls := "anim-area"))
        }
      ).render
      clearElement(dom.document.getElementById("animations")).appendChild(elem)
    }
    animations.foreach { case (animId, animated) => renderAnimation(animId, animated, prevAnimations.get(animId)) }
    prevAnimations = animations
  }

  def render(root: dom.Element) = {
    val e = div(
      cls := "container",
      div(img(src := "diode-logo-small.png")),
      h1("RAF example"),
      p(a(href := "https://github.com/suzaku-io/diode/tree/master/examples/raf", "Source code")),
      div(
        cls := "dropdown",
        button(tpe := "button", data("toggle") := "dropdown", "Add animation", span(cls := "caret")),
        ul(
          cls := "dropdown-menu",
          animGenerators.map {
            case (animName, animGenerator) =>
              li(
                a(href := "#", onclick := { () =>
                  AppCircuit.dispatch(AddAnimation(animGenerator(math.random)))
                }, animName)
              )
          }
        )
      ),
      div(id := "animations") // placeholder for animations
    ).render
    // clear and update contents
    clearElement(root).appendChild(e)
  }
}
