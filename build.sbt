import sbt.Keys.scalaVersion
import sbt.Keys.version
import sbtcrossproject.CrossPlugin.autoImport.CrossType
import sbtcrossproject.CrossPlugin.autoImport.crossProject

scalaVersion in ThisBuild := "2.12.8"

val lagomOriginalVersion = "1.5.1"
val akkaJsVersion        = "1.2.5.23"

lazy val commonSettings = Seq(
  organization := "org.mliarakos.lagomjs",
  version := "0.1.0-SNAPSHOT",
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

def removeCollisions(base: File, target: File): Unit = {
  if (base.exists) {
    if ((base.isFile && ((target.exists && target.isFile) || base.getName.endsWith(".java"))) ||
        (base.isDirectory && target.isDirectory && IO.listFiles(target).forall(_.getName.startsWith(".")))) {
      IO.delete(base)
    } else if (base.isDirectory)
      IO.listFiles(base).foreach(f => removeCollisions(f, new java.io.File(target, f.getName)))
  }
}

def checkoutLagomSources(targetDir: File, version: String): Unit = {
  import org.eclipse.jgit.api._

  if (!targetDir.exists) {
    // Make parent directories
    IO.createDirectory(targetDir)

    // Clone lagom source code
    new CloneCommand()
      .setDirectory(targetDir)
      .setURI("https://github.com/lagom/lagom.git")
      .call()

    val git = Git.open(targetDir)
    git.checkout().setName(s"${version}").call()
  }
}

def copyToSourceDirectory(sourceDir: File, targetDir: File): Unit = {
  IO.delete(targetDir)
  IO.copyDirectory(
    sourceDir,
    targetDir,
    overwrite = true,
    preserveLastModified = true
  )
  (targetDir / ".gitkeep").createNewFile
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

      val sourceTarget = sourceDirectory.value / "shared" / "src" / "main" / "scala"
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

      val sourceTarget = sourceDirectory.value / "shared" / "src" / "main" / "scala"
      copyToSourceDirectory(
        lagomTargetDirectory.value / "service" / "scaladsl" / "api" / "src" / "main" / "scala",
        sourceTarget
      )

      val jsSources = sourceDirectory.value / "js" / "src" / "main" / "scala"
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

      val sourceTarget = sourceDirectory.value / "shared" / "src" / "main" / "scala"
      copyToSourceDirectory(
        lagomTargetDirectory.value / "service" / "core" / "client" / "src" / "main" / "scala",
        sourceTarget
      )

      val jsSources = sourceDirectory.value / "js" / "src" / "main" / "scala"
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

      val srcTarget = sourceDirectory.value / "shared" / "src" / "main" / "scala"
      copyToSourceDirectory(
        lagomTargetDirectory.value / "service" / "scaladsl" / "client" / "src" / "main" / "scala",
        srcTarget
      )

      val jsSources = sourceDirectory.value / "js" / "src" / "main" / "scala"
      removeCollisions(srcTarget, jsSources)
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
    _.dependsOn(`lagomjs-client`.js, `lagomjs-api-scaladsl`.js)
  )

lazy val `lagomjs` = project
  .in(file("."))
  .settings(commonSettings: _*)
  .settings(
    publish := {},
    publishLocal := {}
  )
  .aggregate(
    `lagomjs-api`.js,
    `lagomjs-api-scaladsl`.js,
    `lagomjs-client`.js,
    `lagomjs-client-scaladsl`.js
  )
