enablePlugins(ScalaJSPlugin)

name := "Diode Treeview"

scalaVersion := "2.11.7"

workbenchSettings

bootSnippet := "TreeViewApp().main();"

testFrameworks += new TestFramework("utest.runner.Framework")

libraryDependencies ++= Seq(
  "org.scala-js" %%% "scalajs-dom" % "0.9.0",
  "com.lihaoyi" %%% "scalatags" % "0.5.3",
  "com.lihaoyi" %%% "utest" % "0.3.1" % "test",
  "me.chrons" %%% "diode-core" % "0.5.0-SNAPSHOT"
)
