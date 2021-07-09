import sbt.Keys._
import sbt._
// shadow sbt-scalajs' crossProject and CrossType from Scala.js 0.6.x
import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

ThisBuild / scalafmtOnCompile := true

Global / onChangedBuildSource := ReloadOnSourceChanges

publish / skip := true

val commonSettings = Seq(
  organization := "io.suzaku",
  crossScalaVersions := Seq("2.12.14", "2.13.6"),
  ThisBuild / scalaVersion := "2.13.6",
  scalacOptions := Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-unchecked",
    "-language:experimental.macros",
    "-language:existentials",
    "-Xfatal-warnings",
    "-Xlint",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard"
  ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, 12)) => Seq("-Xlint:-unused", "-language:higherKinds")
    case _             => Nil
  }),
  Compile / scalacOptions -= "-Ywarn-value-discard",
  Compile / doc / scalacOptions -= "-Xfatal-warnings",
  testFrameworks += new TestFramework("utest.runner.Framework"),
  libraryDependencies ++= Seq(
    "com.lihaoyi"            %%% "utest"                  % "0.7.10" % "test",
    "org.scala-lang.modules" %% "scala-collection-compat" % "2.5.0"
  )
)

inThisBuild(
  List(
    homepage := Some(url("https://github.com/suzaku-io/diode")),
    licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer("ochrons",
                "Otto Chrons",
                "",
                url("https://github.com/ochrons"))
    ),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/suzaku-io/diode"),
        "scm:git:git@github.com:suzaku-io/diode.git",
        Some("scm:git:git@github.com:suzaku-io/diode.git")
      )
    ),
    Test / publishArtifact := false
  )
)

val sourceMapSetting =
  Def.setting(
    if (isSnapshot.value) Seq.empty
    else
      Seq({
        val a = baseDirectory.value.toURI.toString.replaceFirst("[^/]+/?$", "")
        val g = "https://raw.githubusercontent.com/suzaku-io/diode"
        s"-P:scalajs:mapSourceURI:$a->$g/v${version.value}/${name.value}/"
      })
  )

lazy val diodeCore = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("diode-core"))
  .settings(commonSettings: _*)
  .settings(
    name := "diode-core",
    libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value % "provided"
  )
  .jsSettings(scalacOptions ++= sourceMapSetting.value)

lazy val diodeData = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("diode-data"))
  .settings(commonSettings: _*)
  .settings(name := "diode-data")
  .jsSettings(scalacOptions ++= sourceMapSetting.value)
  .dependsOn(diodeCore)

lazy val diode = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("diode"))
  .settings(commonSettings: _*)
  .settings(
    name := "diode",
    test := {}
  )
  .dependsOn(diodeCore, diodeData)

lazy val diodeDevtools = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("diode-devtools"))
  .settings(commonSettings: _*)
  .settings(
    name := "diode-devtools"
  )
  .jsSettings(
    libraryDependencies ++= Seq("org.scala-js" %%% "scalajs-dom" % "1.1.0"),
    scalacOptions ++= sourceMapSetting.value
  )
  .dependsOn(diodeCore)

lazy val diodeReact: Project = project
  .enablePlugins(ScalaJSPlugin)
  .in(file("diode-react"))
  .settings(commonSettings: _*)
  .settings(
    name := "diode-react",
    libraryDependencies ++= Seq(
      "com.github.japgolly.scalajs-react" %%% "core" % "1.7.7"
    ),
    scalacOptions ++= sourceMapSetting.value
  )
  .dependsOn(diode.js)

