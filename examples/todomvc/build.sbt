enablePlugins(ScalaJSPlugin)

name := "Diode React TodoMVC"

crossScalaVersions := Seq("2.11.8", "2.12.0")

scalaVersion := "2.12.0"

workbenchSettings

bootSnippet := "TodoMVCApp().main();"

testFrameworks += new TestFramework("utest.runner.Framework")

emitSourceMaps := true

/* create javascript launcher. Searches for an object extends JSApp */
persistLauncher := true

val diodeVersion = "1.1.0"

libraryDependencies ++= Seq(
  "org.scala-js" %%% "scalajs-dom" % "0.9.1",
  "com.github.japgolly.scalajs-react" %%% "core" % "0.11.3",
  "com.github.japgolly.scalajs-react" %%% "extra" % "0.11.3",
  "me.chrons" %%% "diode" % diodeVersion,
  "me.chrons" %%% "diode-devtools" % diodeVersion,
  "me.chrons" %%% "diode-react" % diodeVersion,
  "me.chrons" %%% "boopickle" % "1.2.5"
)

jsDependencies ++= Seq(
  "org.webjars.bower" % "react" % "15.3.2" / "react-with-addons.js" commonJSName "React" minified "react-with-addons.min.js",
  "org.webjars.bower" % "react" % "15.3.2" / "react-dom.js" commonJSName "ReactDOM" minified "react-dom.min.js" dependsOn "react-with-addons.js"
)
