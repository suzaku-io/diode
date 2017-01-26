# Usage with React

Although Diode is totally framework agnostic, it does work very well together with [React](https://facebook.github.io/react/). To simplify integration of Diode
with your [scalajs-react](https://github.com/japgolly/scalajs-react) application, use a separate library `diode-react`.

To use Diode React in your application add following dependency declaration to your Scala.js project.

<pre><code class="lang-scala">"io.suzaku" %%% "diode-react" % "{{ book.version }}"</code></pre>

## Overview

In React the user interface is built out of a hierarchy of _components_, each receiving _props_ and optionally having internal _state_. With Diode you'll want
most of your React components to be _stateless_ because state is managed by the Diode circuit. Sometimes, however, it is useful to maintain some state in the
UI component, for example when editing an item.

A very simple integration can be achieved just by passing a value from a _model reader_ to your React component in its props. This works well for dummy _leaf_
components that just display data. This approach, however, has a few downsides:

1. Your component is not notified of any model changes
2. Your component cannot dispatch any actions

## Wrap and Connect Components

To simplify connecting your Diode circuit to your React components, Diode React provides a way to _wrap_ and _connect_ components. First step is to add the
`ReactConnector` trait to your application `Circuit` class.

```scala
case class RootModel(data: Seq[String], asyncData: Pot[String])

object AppCircuit extends Circuit[RootModel] with ReactConnector[RootModel] { ... }
```

Now your `AppCircuit` is equipped with two new methods: `wrap` and `connect`. Both methods have almost the same signature, taking a zoom function (or
alternatively a model reader). Wrap also takes a function to build a React component immediately whereas connect returns a component taking a similar function as props.

```scala
def wrap[S, C](zoomFunc: M => S)(compB: ModelProxy[S] => C): C
def connect[S](zoomFunc: M => S): ReactConnectProxy[S]
```

The difference between these two is that `wrap` just creates a `ModelProxy` and passes it to your component builder function, while `connect` actually creates
another React component that proxies your own component.

> Use `wrap` when your component doesn't need model updates from Diode, and `connect` when it does. Even if you use `wrap` on a top component, you can still
`connect` components underneath it.

When wrapping or connecting a component, you can either pass the `ModelProxy` directly as props, or use it in the builder function to pass relevant props to
your component.

```scala
// connect with ModelProxy
val smartComponent = ReactComponentB[ModelProxy[Seq[String]]]("SmartComponent").build
...
val sc = AppCircuit.connect(_.data)

// wrap with specific props
case class Props(data: Seq[String], onClick: Callback)
val dummyComponent = ReactComponentB[Props]("DummyComponent").build
...
val dc = AppCircuit.wrap(_.data)(p => dummyComponent(Props(p(), p.dispatch(DummyClicked)))

def render = <.div(sc(p => smartComponent(p)), dc)
```

The `ModelProxy` provides a `dispatchCB` method that wraps the dispatch call in a React `Callback`, making it easy to integrate with event
handlers etc. If you want to dispatch immediately, you can use `dispatchNow` instead. It also provides `wrap` and `connect` methods, allowing your component to
connect sub-components to the Diode circuit.

Note that `connect` is being called once for the lifecycle of this component. Having a single reference to this component during your components lifecycle
ensures that React will update your component rather than unmounting and remounting it. This applies to calling `connect` in other contexts too. Try to connect
and store your component once and reuse it.

```scala
case class State(component: ReactConnectProxy[Pot[String])

val Dashboard = ReactComponentB[ModelProxy[RootModel]]("Dashboard")
.initialState_P(proxy => State(proxy.connect(_.asyncData)))
.renderPS { (_, proxy, state) =>
  <.div(
    <.h3("Data"),
    state.component(p => AsyncDataView(p)), // pass ModelProxy
    proxy.wrap(_.data)(p => DataView(p())), // just pass the value
    <.button(^.onClick --> proxy.dispatchCB(RefreshData), "Refresh")
  )
}
.build
```  

## Rendering `Pot`

Because a `Pot` can exist in many states, it's desirable to reflect these states in your view components. Diode React extends `Pot` by adding convenient
rendering methods through `ReactPot`.

```scala
// use import to get access to implicit extension methods
import diode.react.ReactPot._
```

An example from the [SPA tutorial](https://github.com/ochrons/scalajs-spa-tutorial)

```scala
val Motd = ReactComponentB[ModelProxy[Pot[String]]]("Motd")
  .render_P { proxy =>
    Panel(Panel.Props("Message of the day"),
      // render messages depending on the state of the Pot
      proxy().renderPending(_ > 500, _ => <.p("Loading...")),
      proxy().renderFailed(ex => <.p("Failed to load")),
      proxy().render(m => <.p(m)),
      Button(Button.Props(proxy.dispatchCB(UpdateMotd()), CommonStyle.danger), Icon.refresh, " Update")
    )
  }
```

Each of the rendering functions optionally renders the provided content, depending on the state of `Pot`. In `renderPending` you can supply a condition based
on the duration of the pending request, so that the UI will show a "Loading" message only after some time has elapsed. This of course requires that your action
is updating the model at suitable intervals for the model to update.

## Examples

You may also want to take a look at the [TodoMVC](https://github.com/suzaku-io/diode/tree/master/examples/todomvc) example for how to build React applications
using Diode for application state management.

Another more complete application is example is the [Scala.js SPA tutorial](https://github.com/ochrons/scalajs-spa-tutorial), demonstrating the use of
`ReactPot` as well.
