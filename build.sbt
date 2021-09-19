import sbt.Keys._
import sbt._
// shadow sbt-scalajs' crossProject and CrossType from Scala.js 0.6.x
import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}
import Util._

ThisBuild / scalafmtOnCompile := true

Global / onChangedBuildSource := ReloadOnSourceChanges

publish / skip := true

val scala3Version = "3.0.2"

val commonSettings = Seq(
  organization             := "io.suzaku",
  crossScalaVersions       := Seq("2.13.6"),
  ThisBuild / scalaVersion := "2.13.6",
  scalacOptions := Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-unchecked"
  ),
  scalacOptions ++= scalaVerDependentSeq {
    case (2, _) => Seq(
      "-Xlint",
      "-Ywarn-dead-code",
      "-Ywarn-numeric-widen",
      "-Ywarn-value-discard",
      "-language:experimental.macros",
      "-language:existentials"
    )
  }.value,
  scalacOptions ++= scalaVerDependentSeq {
    case (2, 12) => Seq(
      "-Xfatal-warnings",
      "-Xlint:-unused",
      "-language:higherKinds"
    )
    case (2, 13) => Seq("-Werror")
    case (3, _) => Seq("-Xfatal-warnings")
  }.value,
  Compile / scalacOptions -= scalaVerDependent {
    case (2, _) => "-Ywarn-value-discard"
  }.value,
  Compile / doc / scalacOptions -= scalaVerDependent {
    case (2, 12) | (3, _) => "-Xfatal-warnings"
    case (2, 13) => "-Werror"
  }.value,
  testFrameworks += new TestFramework("utest.runner.Framework"),
  libraryDependencies ++= Seq(
    "com.lihaoyi" %%% "utest" % "0.7.10" % "test"
  ),
  libraryDependencies += scalaVerDependent {
    case (2, _) => "org.scala-lang.modules" %% "scala-collection-compat" % "2.5.0"
  }.value
)

inThisBuild(
  List(
    homepage := Some(url("https://github.com/suzaku-io/diode")),
    licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer("ochrons", "Otto Chrons", "", url("https://github.com/ochrons"))
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

val sourceMapSetting: Def.Initialize[Option[String]] = Def.settingDyn(
  if (isSnapshot.value) Def.setting(None)
  else {
    val a = baseDirectory.value.toURI.toString.replaceFirst("[^/]+/?$", "")
    val g = "https://raw.githubusercontent.com/suzaku-io/diode"
    val uri = s"$a->$g/v${version.value}/${name.value}/"
    scalaVerDependent {
      case (2, _) => s"-P:scalajs:mapSourceURI:$uri"
      case (3, _) => s"-scalajs:mapSourceURI:$uri"
    }
  }
)

val commonJsSettings = Seq(
  scalacOptions += sourceMapSetting.value,
  scalacOptions += scalaVerDependent {
    case (3, _) => "-scalajs"
  }.value
)

lazy val diodeCore = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("diode-core"))
  .settings(commonSettings: _*)
  .settings(
    name := "diode-core",
    crossScalaVersions += scala3Version,
    libraryDependencies += Def.settingDyn {
      val scalaVer = scalaVersion.value
      scalaVerDependent {
        case (2, _) => "org.scala-lang" % "scala-reflect" % scalaVer % "provided"
      }
    }.value
  )
  .jsSettings(commonJsSettings: _*)

lazy val diodeData = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("diode-data"))
  .settings(commonSettings: _*)
  .settings(
    name := "diode-data",
    crossScalaVersions += scala3Version
  )
  .jsSettings(commonJsSettings: _*)
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
  .jsSettings(commonJsSettings: _*)
  .jsSettings(
    libraryDependencies ++= Seq("org.scala-js" %%% "scalajs-dom" % "1.2.0"),
  )
  .dependsOn(diodeCore)

lazy val diodeReact: Project = project
  .enablePlugins(ScalaJSPlugin)
  .in(file("diode-react"))
  .settings(commonSettings: _*)
  .settings(commonJsSettings: _*)
  .settings(
    name := "diode-react",
    libraryDependencies ++= Seq(
      "com.github.japgolly.scalajs-react" %%% "core" % "2.0.0-RC3"
    )
  )
  .dependsOn(diode.js)
