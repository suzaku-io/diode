val scalaJSVersion = sys.env.getOrElse("SCALAJS_VERSION", "1.3.0")

addSbtPlugin("org.scala-js" % "sbt-scalajs" % scalaJSVersion)

addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.0.0")

addSbtPlugin("com.geirsson" % "sbt-scalafmt" % "1.5.1")

addSbtPlugin("com.geirsson" % "sbt-ci-release" % "1.5.3")

