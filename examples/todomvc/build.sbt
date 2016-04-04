enablePlugins(ScalaJSPlugin)

name := "Diode React TodoMVC"

scalaVersion := "2.11.7"

workbenchSettings

bootSnippet := "TodoMVCApp().main();"

testFrameworks += new TestFramework("utest.runner.Framework")

emitSourceMaps := true

/* create javascript launcher. Searches for an object extends JSApp */
persistLauncher := true

val diodeVersion = "0.5.1"

libraryDependencies ++= Seq(
  "org.scala-js" %%% "scalajs-dom" % "0.9.0",
  "com.github.japgolly.scalajs-react" %%% "core" % "0.10.4",
  "com.github.japgolly.scalajs-react" %%% "extra" % "0.10.4",
  "me.chrons" %%% "diode" % diodeVersion,
  "me.chrons" %%% "diode-devtools" % diodeVersion,
  "me.chrons" %%% "diode-react" % diodeVersion,
  "me.chrons" %%% "boopickle" % "1.1.2"
)

jsDependencies ++= Seq(
  "org.webjars.bower" % "react" % "0.14.7" / "react-with-addons.js" commonJSName "React" minified "react-with-addons.min.js",
  "org.webjars.bower" % "react" % "0.14.7" / "react-dom.js" commonJSName "ReactDOM" minified "react-dom.min.js" dependsOn "react-with-addons.js"
)
