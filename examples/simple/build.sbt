enablePlugins(ScalaJSPlugin)

name := "Diode Example"

scalaVersion := "2.11.8"

workbenchSettings

bootSnippet := "SimpleApp().main();"

libraryDependencies ++= Seq(
  "org.scala-js" %%% "scalajs-dom" % "0.9.1",
  "com.lihaoyi" %%% "scalatags" % "0.5.5",
  "me.chrons" %%% "diode-core" % "1.0.0"
)
