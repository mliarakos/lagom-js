import sbt.Keys.scalaVersion
import sbt.Keys.version
import sbtcrossproject.CrossPlugin.autoImport.CrossType
import sbtcrossproject.CrossPlugin.autoImport.crossProject

val scalaVersions = Seq("2.12.9", "2.11.12")

val lagomOriginalVersion = "1.5.1"
val akkaJsVersion        = "1.2.5.23"

lazy val commonSettings = Seq(
  organization := "com.github.mliarakos.lagomjs",
  version := "0.1.0-SNAPSHOT",
  crossScalaVersions := scalaVersions,
  scalaVersion := scalaVersions.head,
  scalacOptions ++= Seq(
    "-encoding",
    "utf8",
    "-deprecation",
    "-feature",
    "-unchecked"
  )
)

lazy val commonJsSettings = Seq(
  scalacOptions += "-P:scalajs:sjsDefinedByDefault"
)

lazy val lagomVersion         = settingKey[String]("The Lagom version to use.")
lazy val lagomTargetDirectory = settingKey[File]("Directory for the Lagom source.")

lazy val assembleLagomLibrary = taskKey[Unit]("Check out the Lagom component source and then apply overrides.")

// Adapted the Git checkout approach used by Akka.js to cross-compile Lagom for JS
// https://github.com/akka-js/akka.js

@inline
def isFileOverride(base: File, target: File): Boolean =
  base.isFile && (target.exists && target.isFile)

@inline
def isDirectoryOverride(base: File, target: File): Boolean =
  base.isDirectory && target.isDirectory && IO.listFiles(target).forall(_.getName.startsWith("."))

def removeCollisions(base: File, target: File): Unit = {
  if (base.exists) {
    if (isFileOverride(base, target) || isDirectoryOverride(base, target))
      IO.delete(base)
    else if (base.isDirectory)
      IO.listFiles(base).foreach(file => removeCollisions(file, target / file.getName))
  }
}

def checkoutLagomSources(targetDirectory: File, version: String): Unit = {
  import org.eclipse.jgit.api._

  if (!targetDirectory.exists) {
    // Make parent directories
    IO.createDirectory(targetDirectory)

    // Clone lagom source code
    new CloneCommand()
      .setDirectory(targetDirectory)
      .setURI("https://github.com/lagom/lagom.git")
      .call()

    val git = Git.open(targetDirectory)
    git.checkout().setName(s"${version}").call()
  }
}

def copyToSourceDirectory(sourceDirectory: File, targetDirectory: File): Unit = {
  IO.delete(targetDirectory)
  IO.copyDirectory(sourceDirectory, targetDirectory, overwrite = true, preserveLastModified = true)
  (targetDirectory / ".gitkeep").createNewFile
}

lazy val `lagomjs-api` = crossProject(JSPlatform)
  .withoutSuffixFor(JSPlatform)
  .crossType(CrossType.Full)
  .in(file("lagomjs-api"))
  .settings(commonSettings: _*)
  .settings(
    name := "lagomjs-api"
  )
  .settings(
    lagomVersion := lagomOriginalVersion,
    lagomTargetDirectory := target.value / "lagomSources" / lagomVersion.value,
    assembleLagomLibrary := {
      checkoutLagomSources(lagomTargetDirectory.value, lagomVersion.value)

      val sourceTarget = file("lagomjs-api") / "shared" / "src" / "main" / "scala"
      copyToSourceDirectory(
        lagomTargetDirectory.value / "service" / "core" / "api" / "src" / "main" / "scala",
        sourceTarget
      )
    }
  )
  .jsSettings(commonJsSettings: _*)
  .jsSettings(
    libraryDependencies ++= Seq(
      "org.scala-lang.modules" %%% "scala-parser-combinators" % "1.1.2",
      "org.akka-js"            %%% "akkajsactor"              % akkaJsVersion,
      "org.akka-js"            %%% "akkajsactorstream"        % akkaJsVersion,
      "com.typesafe.play"      %%% "play-json"                % "2.7.3"
    )
  )
  .jsSettings(
    compile in Compile := { (compile in Compile).dependsOn(assembleLagomLibrary).value },
    publishLocal := { publishLocal.dependsOn(assembleLagomLibrary).value }
  )

lazy val `lagomjs-api-scaladsl` = crossProject(JSPlatform)
  .withoutSuffixFor(JSPlatform)
  .crossType(CrossType.Full)
  .in(file("lagomjs-api-scaladsl"))
  .settings(commonSettings: _*)
  .settings(
    name := "lagomjs-scaladsl-api"
  )
  .settings(
    lagomVersion := lagomOriginalVersion,
    lagomTargetDirectory := target.value / "lagomSources" / lagomVersion.value,
    assembleLagomLibrary := {
      checkoutLagomSources(lagomTargetDirectory.value, lagomVersion.value)

      val sourceTarget = file("lagomjs-api-scaladsl") / "shared" / "src" / "main" / "scala"
      copyToSourceDirectory(
        lagomTargetDirectory.value / "service" / "scaladsl" / "api" / "src" / "main" / "scala",
        sourceTarget
      )

      val jsSources = sourceDirectory.value / "main" / "scala"
      removeCollisions(sourceTarget, jsSources)
    }
  )
  .jsSettings(commonJsSettings: _*)
  .jsSettings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % "provided"
    )
  )
  .jsSettings(
    compile in Compile := { (compile in Compile).dependsOn(assembleLagomLibrary).value },
    publishLocal := { publishLocal.dependsOn(assembleLagomLibrary).value }
  )
  .jsConfigure(
    _.dependsOn(`lagomjs-api`.js)
  )

lazy val `lagomjs-client` = crossProject(JSPlatform)
  .withoutSuffixFor(JSPlatform)
  .crossType(CrossType.Full)
  .in(file("lagomjs-client"))
  .settings(commonSettings: _*)
  .settings(
    name := "lagomjs-client"
  )
  .settings(
    lagomVersion := lagomOriginalVersion,
    lagomTargetDirectory := target.value / "lagomSources" / lagomVersion.value,
    assembleLagomLibrary := {
      checkoutLagomSources(lagomTargetDirectory.value, lagomVersion.value)

      val sourceTarget = file("lagomjs-client") / "shared" / "src" / "main" / "scala"
      copyToSourceDirectory(
        lagomTargetDirectory.value / "service" / "core" / "client" / "src" / "main" / "scala",
        sourceTarget
      )

      val jsSources = sourceDirectory.value / "main" / "scala"
      removeCollisions(sourceTarget, jsSources)
    }
  )
  .jsSettings(commonJsSettings: _*)
  .jsSettings(
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "0.9.7"
    )
  )
  .jsSettings(
    compile in Compile := { (compile in Compile).dependsOn(assembleLagomLibrary).value },
    publishLocal := { publishLocal.dependsOn(assembleLagomLibrary).value }
  )
  .jsConfigure(
    _.dependsOn(`lagomjs-api`.js)
  )

lazy val `lagomjs-client-scaladsl` = crossProject(JSPlatform)
  .withoutSuffixFor(JSPlatform)
  .crossType(CrossType.Full)
  .in(file("lagomjs-client-scaladsl"))
  .settings(commonSettings: _*)
  .settings(
    name := "lagomjs-scaladsl-client"
  )
  .settings(
    lagomVersion := lagomOriginalVersion,
    lagomTargetDirectory := target.value / "lagomSources" / lagomVersion.value,
    assembleLagomLibrary := {
      checkoutLagomSources(lagomTargetDirectory.value, lagomVersion.value)

      val sourceTarget = file("lagomjs-client-scaladsl") / "shared" / "src" / "main" / "scala"
      copyToSourceDirectory(
        lagomTargetDirectory.value / "service" / "scaladsl" / "client" / "src" / "main" / "scala",
        sourceTarget
      )

      val jsSources = sourceDirectory.value / "main" / "scala"
      removeCollisions(sourceTarget, jsSources)
    }
  )
  .jsSettings(commonJsSettings: _*)
  .jsSettings(
    compile in Compile := { (compile in Compile).dependsOn(assembleLagomLibrary).value },
    publishLocal := { publishLocal.dependsOn(assembleLagomLibrary).value }
  )
  .jsConfigure(
    _.dependsOn(`lagomjs-client`.js, `lagomjs-api-scaladsl`.js)
  )

lazy val `lagomjs-persistence-scaladsl` = crossProject(JSPlatform)
  .withoutSuffixFor(JSPlatform)
  .crossType(CrossType.Full)
  .in(file("lagomjs-persistence-scaladsl"))
  .settings(commonSettings: _*)
  .settings(
    name := "lagomjs-scaladsl-persistence"
  )
  .settings(
    lagomVersion := lagomOriginalVersion,
    lagomTargetDirectory := target.value / "lagomSources" / lagomVersion.value,
    assembleLagomLibrary := {
      checkoutLagomSources(lagomTargetDirectory.value, lagomVersion.value)

      val sourceTarget = file("lagomjs-persistence-scaladsl") / "shared" / "src" / "main" / "scala"
      copyToSourceDirectory(
        lagomTargetDirectory.value / "persistence" / "scaladsl" / "src" / "main" / "scala",
        sourceTarget
      )

      val jsSources = sourceDirectory.value / "main" / "scala"
      removeCollisions(sourceTarget, jsSources)
    }
  )
  .jsSettings(commonJsSettings: _*)
  .jsSettings(
    compile in Compile := { (compile in Compile).dependsOn(assembleLagomLibrary).value },
    publishLocal := { publishLocal.dependsOn(assembleLagomLibrary).value }
  )
  .jsConfigure(
    _.dependsOn(`lagomjs-api-scaladsl`.js)
  )

lazy val `lagomjs` = project
  .in(file("."))
  .settings(commonSettings: _*)
  .settings(
    publish / skip := true,
    publishLocal / skip := true
  )
  .aggregate(
    `lagomjs-api`.js,
    `lagomjs-api-scaladsl`.js,
    `lagomjs-client`.js,
    `lagomjs-client-scaladsl`.js,
    `lagomjs-persistence-scaladsl`.js
  )
