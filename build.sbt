import sbt.Keys.scalaVersion
import sbt.Keys.version
import sbtcrossproject.CrossPlugin.autoImport.crossProject
import sbtcrossproject.CrossPlugin.autoImport.CrossType

scalaVersion in ThisBuild := "2.12.8"

val lagomOriginalVersion = "1.5.1"

val akkaJsVersion = "1.2.5.23"

lazy val commonSettings = Seq(
  organization := "org.mliarakos.lagom-scalajs",
  version := "0.1.0",
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

lazy val lagomVersion   = settingKey[String]("lagomVersion")
lazy val lagomTargetDir = settingKey[File]("lagomTargetDir")

lazy val assembleLagomLibrary = taskKey[Unit]("Check out lagom standard library and then apply overrides.")

def rm_clash(base: File, target: File): Unit = {
  if (base.exists) {
    if ((base.isFile && ((target.exists && target.isFile) || base.getName.endsWith(".java"))) ||
        (base.isDirectory && target.isDirectory && IO.listFiles(target).forall(_.getName.startsWith(".")))) {
      IO.delete(base)
    } else if (base.isDirectory)
      IO.listFiles(base).foreach(f => rm_clash(f, new java.io.File(target, f.getName)))
  }
}

def getLagomSources(targetDir: File, version: String): Unit = {
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

def copyToSourceFolder(sourceDir: File, targetDir: File): Unit = {
  IO.delete(targetDir)
  IO.copyDirectory(
    sourceDir,
    targetDir,
    overwrite = true,
    preserveLastModified = true
  )
  (targetDir / ".gitkeep").createNewFile
}

lazy val `lagom-js-api` = crossProject(JSPlatform)
  .crossType(CrossType.Full)
  .in(file("lagom-js-api"))
  .settings(commonSettings: _*)
  .settings(
    lagomVersion := lagomOriginalVersion,
    lagomTargetDir := target.value / "lagomSources" / lagomVersion.value,
    assembleLagomLibrary := {
      getLagomSources(lagomTargetDir.value, lagomVersion.value)
      val srcTarget = file("lagom-js-api/shared/src/main/scala")
      copyToSourceFolder(
        lagomTargetDir.value / "service" / "core" / "api" / "src" / "main" / "scala",
        srcTarget
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
    compile in Compile := {
      (compile in Compile).dependsOn(assembleLagomLibrary).value
    }
  )

lazy val `lagom-js-api-scaladsl` = crossProject(JSPlatform)
  .crossType(CrossType.Full)
  .in(file("lagom-js-api-scaladsl"))
  .settings(commonSettings: _*)
  .settings(
    lagomVersion := lagomOriginalVersion,
    lagomTargetDir := target.value / "lagomSources" / lagomVersion.value,
    assembleLagomLibrary := {
      getLagomSources(lagomTargetDir.value, lagomVersion.value)

      val srcTarget = file("lagom-js-api-scaladsl/shared/src/main/scala")
      copyToSourceFolder(
        lagomTargetDir.value / "service" / "scaladsl" / "api" / "src" / "main" / "scala",
        srcTarget
      )

      val jsSources = file("lagom-js-api-scaladsl/js/src/main/scala")
      rm_clash(srcTarget, jsSources)
    }
  )
  .jsSettings(commonJsSettings: _*)
  .jsSettings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % "provided"
    )
  )
  .jsSettings(
    compile in Compile := {
      (compile in Compile).dependsOn(assembleLagomLibrary).value
    }
  )
  .jsConfigure(_.dependsOn(`lagom-js-api`.js))

lazy val `lagom-js-client` = crossProject(JSPlatform)
  .crossType(CrossType.Full)
  .in(file("lagom-js-client"))
  .settings(commonSettings: _*)
  .settings(
    lagomVersion := lagomOriginalVersion,
    lagomTargetDir := target.value / "lagomSources" / lagomVersion.value,
    assembleLagomLibrary := {
      getLagomSources(lagomTargetDir.value, lagomVersion.value)

      val srcTarget = file("lagom-js-client/shared/src/main/scala")
      copyToSourceFolder(
        lagomTargetDir.value / "service" / "core" / "client" / "src" / "main" / "scala",
        srcTarget
      )

      val jsSources = file("lagom-js-client/js/src/main/scala")
      rm_clash(srcTarget, jsSources)
    }
  )
  .jsSettings(commonJsSettings: _*)
  .jsSettings(
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "0.9.7"
    )
  )
  .jsSettings(
    compile in Compile := {
      (compile in Compile).dependsOn(assembleLagomLibrary).value
    }
  )
  .jsConfigure(_.dependsOn(`lagom-js-api`.js))

lazy val `lagom-js-client-scaladsl` = crossProject(JSPlatform)
  .crossType(CrossType.Full)
  .in(file("lagom-js-client-scaladsl"))
  .settings(commonSettings: _*)
  .settings(
    lagomVersion := lagomOriginalVersion,
    lagomTargetDir := target.value / "lagomSources" / lagomVersion.value,
    assembleLagomLibrary := {
      getLagomSources(lagomTargetDir.value, lagomVersion.value)

      val srcTarget = file("lagom-js-client-scaladsl/shared/src/main/scala")
      copyToSourceFolder(
        lagomTargetDir.value / "service" / "scaladsl" / "client" / "src" / "main" / "scala",
        srcTarget
      )

      val jsSources = file("lagom-js-client-scaladsl/js/src/main/scala")
      rm_clash(srcTarget, jsSources)
    }
  )
  .jsSettings(commonJsSettings: _*)
  .jsSettings(
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "0.9.7"
    )
  )
  .jsSettings(
    compile in Compile := {
      (compile in Compile).dependsOn(assembleLagomLibrary).value
    }
  )
  .jsConfigure(_.dependsOn(`lagom-js-client`.js, `lagom-js-api-scaladsl`.js))

lazy val root = project
  .in(file("."))
  .settings(commonSettings: _*)
  .aggregate(
    `lagom-js-api`.js,
    `lagom-js-api-scaladsl`.js,
    `lagom-js-client`.js,
    `lagom-js-client-scaladsl`.js
  )
