enablePlugins(ScalaJSPlugin, WorkbenchPlugin)

name := "Diode RAF Example"

crossScalaVersions := Seq("2.11.11", "2.12.2")

scalaVersion := "2.12.2"

// create javascript launcher. Searches for an object extends JSApp
persistLauncher := true

libraryDependencies ++= Seq(
  "org.scala-js" %%% "scalajs-dom" % "0.9.1",
  "com.lihaoyi"  %%% "scalatags"   % "0.6.2",
  "io.suzaku"    %%% "diode-core"  % "1.1.2"
)

workbenchDefaultRootObject := Some(("target/scala-2.12/classes/index.html", "target/scala-2.12/"))
