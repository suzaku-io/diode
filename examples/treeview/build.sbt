enablePlugins(ScalaJSPlugin)

name := "Diode Treeview"

scalaVersion := "2.11.8"

workbenchSettings

bootSnippet := "TreeViewApp().main();"

testFrameworks += new TestFramework("utest.runner.Framework")

libraryDependencies ++= Seq(
  "org.scala-js" %%% "scalajs-dom" % "0.9.1",
  "com.lihaoyi" %%% "scalatags" % "0.5.5",
  "com.lihaoyi" %%% "utest" % "0.4.3" % "test",
  "me.chrons" %%% "diode-core" % "1.0.0"
)
