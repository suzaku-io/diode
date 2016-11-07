enablePlugins(ScalaJSPlugin)

name := "Diode Example"

crossScalaVersions := Seq("2.11.8", "2.12.0")

scalaVersion := "2.12.0"

workbenchSettings

bootSnippet := "SimpleApp().main();"

libraryDependencies ++= Seq(
  "org.scala-js" %%% "scalajs-dom" % "0.9.1",
  "com.lihaoyi" %%% "scalatags" % "0.6.2",
  "me.chrons" %%% "diode-core" % "1.1.0"
)
