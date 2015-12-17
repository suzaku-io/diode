enablePlugins(ScalaJSPlugin)

name := "Diode Example"

scalaVersion := "2.11.7"

workbenchSettings

bootSnippet := "SimpleApp().main();"

libraryDependencies ++= Seq(
  "org.scala-js" %%% "scalajs-dom" % "0.8.2",
  "com.lihaoyi" %%% "scalatags" % "0.5.3",
  "me.chrons" %%% "diode" % "0.3.0-SNAPSHOT"
)
