# Usage with React

Very Much WorkInProgress!!! :)

1. Include `diode-react` in your project

```scala
libraryDependencies += "me.chrons" %% "diode-react" % "0.1.0-SNAPSHOT"
```

2. Extend your Circuit with `ReactConnector`

```scala
case class RootModel(auth: Option[Auth], lastError: String, workStatus: Pot[WorkStatus])

object AppModel extends Circuit[RootModel] with ReactConnector[RootModel] {
```

3. Define a React component with props containing `ComponentModel[A]`

```scala
private val component = ReactComponentB[ComponentModel[Option[Auth]]]("AuthUser")
  .render_P(cm => {
    import cm._
    <.div(model match {
      case Some(auth) =>
        <.button(^.onClick --> (dispatch(Logout) >> dispatch(SetError("Logged out"))), auth.user.name)
      case None =>
        <.button(^.onClick --> (dispatch(Login(Auth("token", User("id", "User Name")))) >> dispatch(SetError("Logged in"))), "Login")
    })
  })
  .build
```

4. Connect your component to the Circuit

```scala
<.div(
  <.h3("Authenticate",
    AppModel.connect(_.auth)(AuthUser(_))
  )
)
```
