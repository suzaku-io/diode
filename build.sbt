import sbt.Keys._
import sbt._
// shadow sbt-scalajs' crossProject and CrossType from Scala.js 0.6.x
import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

ThisBuild / scalafmtOnCompile := true

Global / onChangedBuildSource := ReloadOnSourceChanges

val customScalaJSVersion = Option(System.getenv("SCALAJS_VERSION"))

val commonSettings = Seq(
  organization := "io.suzaku",
  crossScalaVersions := Seq("2.12.11", "2.13.2"),
  scalaVersion in ThisBuild := "2.13.2",
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
    "com.lihaoyi"            %%% "utest"                  % "0.7.5" % "test",
    "org.scala-lang.modules" %% "scala-collection-compat" % "2.3.1"
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

def preventPublication(p: Project) =
  p.settings(
    publish := (()),
    publishLocal := (()),
    publishArtifact := false,
    publishTo := Some(
      Resolver.file("Unused transient repository", target.value / "fakepublish")
    ),
    packagedArtifacts := Map.empty
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
  .jvmSettings(
    skip.in(publish) := customScalaJSVersion.isDefined
  )

lazy val diodeData = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("diode-data"))
  .settings(commonSettings: _*)
  .settings(name := "diode-data")
  .jsSettings(scalacOptions ++= sourceMapSetting.value)
  .jvmSettings(
    skip.in(publish) := customScalaJSVersion.isDefined
  )
  .dependsOn(diodeCore)

lazy val diode = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("diode"))
  .settings(commonSettings: _*)
  .settings(
    name := "diode",
    test := {}
  )
  .jvmSettings(
    skip.in(publish) := customScalaJSVersion.isDefined
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
  .jvmSettings(
    skip.in(publish) := customScalaJSVersion.isDefined
  )
  .dependsOn(diodeCore)

lazy val diodeReact: Project = project
  .enablePlugins(ScalaJSPlugin)
  .in(file("diode-react"))
  .settings(commonSettings: _*)
  .settings(
    name := "diode-react",
    libraryDependencies ++= Seq(
      "com.github.japgolly.scalajs-react" %%% "core" % Version.sjsReact
    ),
    scalacOptions ++= sourceMapSetting.value
  )
  .dependsOn(diode.js)

lazy val coreProjects = Seq[ProjectReference](
  diode.js,
  diode.jvm,
  diodeCore.js,
  diodeCore.jvm,
  diodeData.js,
  diodeData.jvm,
  diodeDevtools.js,
  diodeDevtools.jvm,
  diodeReact
)

lazy val root = preventPublication(project.in(file(".")))
  .settings(
    commonSettings
  )
  .aggregate(coreProjects: _*)
