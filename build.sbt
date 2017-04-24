import sbt._
import Keys._
import com.typesafe.sbt.pgp.PgpKeys._

crossScalaVersions := Seq("2.11.8", "2.12.1")

val commonSettings = Seq(
  organization := "io.suzaku",
  version := Version.library,
  scalaVersion := "2.11.8",
  scalacOptions := Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-unchecked",
    "-language:experimental.macros",
    "-Xfatal-warnings",
    "-Xlint",
    "-Yno-adapted-args",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard",
    "-Xfuture"
  ),
  scalacOptions in Compile -= "-Ywarn-value-discard",
  scalacOptions in (Compile, doc) -= "-Xfatal-warnings",
  testFrameworks += new TestFramework("utest.runner.Framework"),
  libraryDependencies ++= Seq(
    "com.lihaoyi" %%% "utest" % "0.4.4" % "test"
  )
)

val publishSettings = Seq(
  scmInfo := Some(
    ScmInfo(url("https://github.com/suzaku-io/diode"),
            "scm:git:git@github.com:suzaku-io/diode.git",
            Some("scm:git:git@github.com:suzaku-io/diode.git"))),
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomExtra :=
    <url>https://github.com/suzaku-io/diode</url>
      <licenses>
        <license>
          <name>MIT license</name>
          <url>http://www.opensource.org/licenses/mit-license.php</url>
        </license>
      </licenses>
      <developers>
        <developer>
          <id>ochrons</id>
          <name>Otto Chrons</name>
          <url>https://github.com/ochrons</url>
        </developer>
      </developers>,
  pomIncludeRepository := { _ =>
    false
  },
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
  }
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
    publish := (),
    publishLocal := (),
    publishSigned := (),
    publishLocalSigned := (),
    publishArtifact := false,
    publishTo := Some(Resolver.file("Unused transient repository", target.value / "fakepublish")),
    packagedArtifacts := Map.empty
  )

lazy val diodeCore = crossProject
  .in(file("diode-core"))
  .settings(commonSettings: _*)
  .settings(publishSettings: _*)
  .settings(
    name := "diode-core",
    libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value % "provided"
  )
  .jsSettings(
    scalacOptions ++= sourceMapSetting.value
  )
  .jvmSettings()

lazy val diodeCoreJS = diodeCore.js

lazy val diodeCoreJVM = diodeCore.jvm

lazy val diodeData = crossProject
  .in(file("diode-data"))
  .settings(commonSettings: _*)
  .settings(publishSettings: _*)
  .settings(
    name := "diode-data"
  )
  .jsSettings(
    scalacOptions ++= sourceMapSetting.value
  )
  .jvmSettings()
  .dependsOn(diodeCore)

lazy val diodeDataJS = diodeData.js

lazy val diodeDataJVM = diodeData.jvm

lazy val diode = crossProject
  .settings(commonSettings: _*)
  .settings(publishSettings: _*)
  .settings(
    name := "diode"
  )
  .dependsOn(diodeCore, diodeData)

lazy val diodeJS = diode.js

lazy val diodeJVM = diode.jvm

lazy val diodeDevtools = crossProject
  .in(file("diode-devtools"))
  .settings(commonSettings: _*)
  .settings(publishSettings: _*)
  .settings(
    name := "diode-devtools"
  )
  .jsSettings(
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "0.9.1"
    ),
    scalacOptions ++= sourceMapSetting.value
  )
  .jvmSettings()
  .dependsOn(diodeCore)

lazy val diodeDevToolsJS = diodeDevtools.js

lazy val diodeDevToolsJVM = diodeDevtools.jvm

lazy val diodeReact = project
  .in(file("diode-react"))
  .settings(commonSettings: _*)
  .settings(publishSettings: _*)
  .settings(
    name := "diode-react",
    libraryDependencies ++= Seq(
      "com.github.japgolly.scalajs-react" %%% "core" % "1.0.0-RC1"
    ),
    scalacOptions ++= sourceMapSetting.value
  )
  .dependsOn(diodeJS)
  .enablePlugins(ScalaJSPlugin)

lazy val root = preventPublication(project.in(file(".")))
  .settings(commonSettings: _*)
  .aggregate(diodeJS, diodeJVM, diodeCoreJS, diodeCoreJVM, diodeDataJS, diodeDataJVM, diodeReact, diodeDevToolsJS, diodeDevToolsJVM)
