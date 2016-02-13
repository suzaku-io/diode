enablePlugins(ScalaJSPlugin)

name := "Diode Example"

scalaVersion := "2.11.7"

workbenchSettings

bootSnippet := "SimpleApp().main();"

libraryDependencies ++= Seq(
  "org.scala-js" %%% "scalajs-dom" % "0.9.0",
  "com.lihaoyi" %%% "scalatags" % "0.5.3",
  "me.chrons" %%% "diode-core" % "0.5.0-SNAPSHOT"
)
