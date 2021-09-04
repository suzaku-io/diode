val scalaJSVersion = sys.env.getOrElse("SCALAJS_VERSION", "1.7.0")

addSbtPlugin("org.scala-js" % "sbt-scalajs" % scalaJSVersion)

addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.1.0")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.3")

addSbtPlugin("com.geirsson" % "sbt-ci-release" % "1.5.7")

