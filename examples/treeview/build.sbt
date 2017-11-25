enablePlugins(ScalaJSPlugin, WorkbenchPlugin)

name := "Diode Treeview"

scalaVersion := "2.12.4"

testFrameworks += new TestFramework("utest.runner.Framework")

scalaJSUseMainModuleInitializer := true

libraryDependencies ++= Seq(
  "org.scala-js" %%% "scalajs-dom" % "0.9.1",
  "com.lihaoyi"  %%% "scalatags"   % "0.6.2",
  "com.lihaoyi"  %%% "utest"       % "0.5.3" % "test",
  "io.suzaku"    %%% "diode-core"  % "1.1.3"
)

workbenchDefaultRootObject := Some(("target/scala-2.12/classes/index.html", "target/scala-2.12"))
