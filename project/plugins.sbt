val scalaJSVersion = sys.env.getOrElse("SCALAJS_VERSION", "1.0.0")

addSbtPlugin("org.scala-js" % "sbt-scalajs" % scalaJSVersion)

addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "0.6.1")

addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.1")

addSbtPlugin("com.geirsson" % "sbt-scalafmt" % "1.5.1")
