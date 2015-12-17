enablePlugins(ScalaJSPlugin)

name := "Diode Treeview"

scalaVersion := "2.11.7"

workbenchSettings

bootSnippet := "TreeViewApp().main();"

testFrameworks += new TestFramework("utest.runner.Framework")

libraryDependencies ++= Seq(
  "org.scala-js" %%% "scalajs-dom" % "0.8.2",
  "com.lihaoyi" %%% "scalatags" % "0.5.3",
  "com.lihaoyi" %%% "utest" % "0.3.1" % "test",
  "me.chrons" %%% "diode" % "0.3.0-SNAPSHOT"
)
