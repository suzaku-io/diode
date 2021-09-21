enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin, WorkbenchPlugin)

name := "Diode React TodoMVC"

scalaVersion := "2.12.8"

testFrameworks += new TestFramework("utest.runner.Framework")

emitSourceMaps := true

/* create javascript launcher. Searches for an object extends JSApp */
scalaJSUseMainModuleInitializer := true

val diodeVersion = "1.1.5"

libraryDependencies ++= Seq(
  "org.scala-js"                      %%% "scalajs-dom"    % "0.9.3",
  "com.github.japgolly.scalajs-react" %%% "core"           % "1.4.2",
  "com.github.japgolly.scalajs-react" %%% "extra"          % "1.4.2",
  "io.suzaku"                         %%% "diode"          % diodeVersion,
  "io.suzaku"                         %%% "diode-devtools" % diodeVersion,
  "io.suzaku"                         %%% "diode-react"    % s"$diodeVersion.142",
  "io.suzaku"                         %%% "boopickle"      % "1.3.0"
)

Compile / npmDependencies ++= Seq("react" -> "16.7.0", "react-dom" -> "16.7.0")

workbenchDefaultRootObject := Some(("target/scala-2.12/classes/index.html", "target/scala-2.12/"))
