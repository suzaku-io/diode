enablePlugins(ScalaJSPlugin)

name := "Diode React TodoMVC"

crossScalaVersions := Seq("2.11.8", "2.12.1")

scalaVersion := "2.12.1"

workbenchSettings

bootSnippet := "TodoMVCApp().main();"

testFrameworks += new TestFramework("utest.runner.Framework")

emitSourceMaps := true

/* create javascript launcher. Searches for an object extends JSApp */
persistLauncher := true

val diodeVersion = "1.1.2-SNAPSHOT"

libraryDependencies ++= Seq(
  "org.scala-js"                      %%% "scalajs-dom"    % "0.9.1",
  "com.github.japgolly.scalajs-react" %%% "core"           % "1.0.0",
  "com.github.japgolly.scalajs-react" %%% "extra"          % "1.0.0",
  "io.suzaku"                         %%% "diode"          % diodeVersion,
  "io.suzaku"                         %%% "diode-devtools" % diodeVersion,
  "io.suzaku"                         %%% "diode-react"    % diodeVersion,
  "io.suzaku"                         %%% "boopickle"      % "1.2.6"
)

jsDependencies ++= Seq(
  "org.webjars.bower" % "react" % "15.5.4" / "react-with-addons.js" commonJSName "React" minified "react-with-addons.min.js",
  "org.webjars.bower" % "react" % "15.5.4" / "react-dom.js" commonJSName "ReactDOM" minified "react-dom.min.js" dependsOn "react-with-addons.js"
)
