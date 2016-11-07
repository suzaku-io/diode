enablePlugins(ScalaJSPlugin)

name := "Diode Treeview"

crossScalaVersions := Seq("2.11.8", "2.12.0")

scalaVersion := "2.12.0"

workbenchSettings

bootSnippet := "TreeViewApp().main();"

testFrameworks += new TestFramework("utest.runner.Framework")

libraryDependencies ++= Seq(
  "org.scala-js" %%% "scalajs-dom" % "0.9.1",
  "com.lihaoyi" %%% "scalatags" % "0.6.2",
  "com.lihaoyi" %%% "utest" % "0.4.4" % "test",
  "me.chrons" %%% "diode-core" % "1.1.0"
)
