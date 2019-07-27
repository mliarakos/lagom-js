import sbt.Keys.{scalaVersion, version}

scalaVersion in ThisBuild := "2.12.8"

lazy val commonSettings = Seq(
  organization := "org.mliarakos.lagom-scalajs",
  version := "0.1.0"
)
