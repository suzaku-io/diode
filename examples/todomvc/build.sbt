enablePlugins(ScalaJSPlugin, WorkbenchPlugin)

name := "Diode React TodoMVC"

scalaVersion := "2.12.4"

testFrameworks += new TestFramework("utest.runner.Framework")

emitSourceMaps := true

/* create javascript launcher. Searches for an object extends JSApp */
scalaJSUseMainModuleInitializer := true

val diodeVersion = "1.1.3"

libraryDependencies ++= Seq(
  "org.scala-js"                      %%% "scalajs-dom"    % "0.9.3",
  "com.github.japgolly.scalajs-react" %%% "core"           % "1.1.0",
  "com.github.japgolly.scalajs-react" %%% "extra"          % "1.1.0",
  "io.suzaku"                         %%% "diode"          % diodeVersion,
  "io.suzaku"                         %%% "diode-devtools" % diodeVersion,
  "io.suzaku"                         %%% "diode-react"    % diodeVersion,
  "io.suzaku"                         %%% "boopickle"      % "1.2.6"
)

jsDependencies ++= Seq(
  "org.webjars.bower" % "react" % "15.6.1" / "react-with-addons.js" commonJSName "React" minified "react-with-addons.min.js",
  "org.webjars.bower" % "react" % "15.6.1" / "react-dom.js" commonJSName "ReactDOM" minified "react-dom.min.js" dependsOn "react-with-addons.js"
)

workbenchDefaultRootObject := Some(("target/scala-2.12/classes/index.html", "target/scala-2.12/"))
