enablePlugins(ScalaJSPlugin, WorkbenchPlugin)

name := "Diode RAF Example"

scalaVersion := "2.12.6"

scalaJSUseMainModuleInitializer := true

libraryDependencies ++= Seq(
  "org.scala-js" %%% "scalajs-dom" % "0.9.3",
  "com.lihaoyi"  %%% "scalatags"   % "0.6.7",
  "io.suzaku"    %%% "diode-core"  % "1.1.4"
)

workbenchDefaultRootObject := Some(("target/scala-2.12/classes/index.html", "target/scala-2.12/"))
