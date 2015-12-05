enablePlugins(ScalaJSPlugin)

name := "Diode React TodoMVC"

scalaVersion := "2.11.7"

workbenchSettings

bootSnippet := "TodoMVCApp().main();"

testFrameworks += new TestFramework("utest.runner.Framework")

emitSourceMaps := true

/* create javascript launcher. Searches for an object extends JSApp */
persistLauncher := true

libraryDependencies ++= Seq(
  "org.scala-js" %%% "scalajs-dom" % "0.8.2",
  "com.github.japgolly.scalajs-react" %%% "core" % "0.10.2",
  "com.github.japgolly.scalajs-react" %%% "extra" % "0.10.2",
  "me.chrons" %%% "diode" % "0.2.1-SNAPSHOT",
  "me.chrons" %%% "diode-react" % "0.2.1-SNAPSHOT"
)

jsDependencies ++= Seq(
  "org.webjars.bower" % "react" % "0.14.3" / "react-with-addons.js" commonJSName "React" minified "react-with-addons.min.js",
  "org.webjars.bower" % "react" % "0.14.3" / "react-dom.js" commonJSName "ReactDOM" minified "react-dom.min.js" dependsOn "react-with-addons.js"
)
