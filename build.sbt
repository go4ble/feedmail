ThisBuild / scalaVersion     := "2.13.10"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "io.github.go4ble"

lazy val root = (project in file("."))
  .settings(
    name := "FeedMail",
    libraryDependencies += "com.rometools" % "rome" % "2.1.0", // feed parsing
    libraryDependencies += "com.h2database" % "h2" % "2.1.214",
    libraryDependencies += "com.typesafe.slick" %% "slick" % "3.4.1",
    libraryDependencies += "com.iheart" %% "ficus" % "1.5.2", // typesafe config utilities
    libraryDependencies += "com.typesafe.akka" %% "akka-actor-typed" % "2.8.0",
    libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.4.6",
    libraryDependencies += "org.simplejavamail" % "simple-java-mail" % "8.0.0",
    libraryDependencies += "com.softwaremill.sttp.client3" %% "core" % "3.8.13" // http client
  )
  .enablePlugins(SbtTwirl)

import com.typesafe.sbt.packager.docker._
enablePlugins(JavaAppPackaging)
dockerBaseImage := "eclipse-temurin:17-jre-alpine"
dockerRepository := Some("ghcr.io/go4ble")
dockerUpdateLatest := true
dockerCommands := {
  // Update docker commands to install bash right before existing final RUN command
  val insertAt = dockerCommands.value.lastIndexWhere(_.makeContent startsWith "RUN")
  val commands = Seq("apk", "add", "bash", "&&",
                     "mkdir", "-p", "/opt/docker/db", "&&",
                     "chown", "1001", "/opt/docker/db")
  dockerCommands.value.patch(insertAt, Seq(Cmd("RUN", commands:_*)), 0)
}
dockerCmd := Seq("-Dconfig.file=application.conf")
