addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.12.1")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.6.2")

addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.14.7")

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.22.0")

libraryDependencies += ("org.scala-js" %% "scalajs-env-jsdom-nodejs" % "1.1.1")
  // published for 2.13 only, and sbt-scalajs already brings its _3 transitives
  .cross(CrossVersion.for3Use2_13)
  .exclude("org.scala-js", "scalajs-env-nodejs_2.13")
  .exclude("org.scala-js", "scalajs-js-envs_2.13")
  .exclude("org.scala-js", "scalajs-logging_2.13")

addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.5.12")

addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "1.1.6")

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.13.1")
