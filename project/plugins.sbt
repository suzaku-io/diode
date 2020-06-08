val scalaJSVersion = sys.env.getOrElse("SCALAJS_VERSION", "1.0.1")

addSbtPlugin("org.scala-js" % "sbt-scalajs" % scalaJSVersion)

addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.0.0")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.0.0")

addSbtPlugin("com.geirsson" % "sbt-ci-release" % "1.5.3")

