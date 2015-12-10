import sbt._
import Keys._
import com.typesafe.sbt.pgp.PgpKeys._

crossScalaVersions := Seq("2.11.7")

val commonSettings = Seq(
  organization := "me.chrons",
  version := Version.library,
  scalaVersion := "2.11.7",
  scalacOptions ++= Seq("-unchecked", "-feature", "-deprecation", "-encoding", "utf8"),
  testFrameworks += new TestFramework("utest.runner.Framework"),
  libraryDependencies ++= Seq(
    "com.lihaoyi" %%% "utest" % "0.3.1" % "test"
  )
)

val publishSettings = Seq(
  scmInfo := Some(ScmInfo(
    url("https://github.com/ochrons/diode"),
    "scm:git:git@github.com:ochrons/diode.git",
    Some("scm:git:git@github.com:ochrons/diode.git"))),
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomExtra :=
    <url>https://github.com/ochrons/diode</url>
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
  pomIncludeRepository := { _ => false },
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
    else Seq({
      val a = baseDirectory.value.toURI.toString.replaceFirst("[^/]+/?$", "")
      val g = "https://raw.githubusercontent.com/ochrons/diode"
      s"-P:scalajs:mapSourceURI:$a->$g/v${version.value}/"
    })
  )

def preventPublication(p: Project) =
  p.settings(
    publish :=(),
    publishLocal :=(),
    publishSigned :=(),
    publishLocalSigned :=(),
    publishArtifact := false,
    publishTo := Some(Resolver.file("Unused transient repository", target.value / "fakepublish")),
    packagedArtifacts := Map.empty)

lazy val diode = crossProject
  .settings(commonSettings: _*)
  .settings(publishSettings: _*)
  .settings(
    name := "diode"
  )
  .jsSettings(scalacOptions ++= sourceMapSetting.value)
  .jvmSettings()

lazy val diodeJS = diode.js

lazy val diodeJVM = diode.jvm

lazy val diodeDevtools = crossProject.in(file("diode-devtools"))
  .settings(commonSettings: _*)
  .settings(publishSettings: _*)
  .settings(
    name := "diode-devtools"
  )
  .jsSettings(
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "0.8.2"
    ),
    scalacOptions ++= sourceMapSetting.value
  )
  .jvmSettings()
  .dependsOn(diode)

lazy val diodeDevToolsJS = diodeDevtools.js

lazy val diodeDevToolsJVM = diodeDevtools.jvm

lazy val diodeReact = project.in(file("diode-react"))
  .settings(commonSettings: _*)
  .settings(publishSettings: _*)
  .settings(
    name := "diode-react",
    libraryDependencies ++= Seq(
      "com.github.japgolly.scalajs-react" %%% "core" % "0.10.0"
    ),
    // use PhantomJS for testing, because we need real browser JS stuff
    scalaJSStage in Global := FastOptStage,
    jsDependencies += RuntimeDOM,
    scalacOptions ++= sourceMapSetting.value
  )
  .dependsOn(diodeJS)
  .enablePlugins(ScalaJSPlugin)

lazy val root = preventPublication(project.in(file(".")))
  .settings()
  .aggregate(diodeJS, diodeJVM, diodeReact, diodeDevToolsJS, diodeDevToolsJVM)
