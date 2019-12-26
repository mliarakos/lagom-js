import sbt.Keys.scalaVersion
import sbt.Keys.version
import sbtcrossproject.CrossPlugin.autoImport.CrossType
import sbtcrossproject.CrossPlugin.autoImport.crossProject

val scalaVersions = Seq("2.12.10")

val baseLagomVersion = "1.6.0"
val akkaJsVersion    = "2.2.6.1"

lazy val scalaSettings = Seq(
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

lazy val publishSettings = Seq(
  homepage := Some(url("https://github.com/mliarakos/lagom-js")),
  licenses := Seq(("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0"))),
  organizationHomepage := Some(url("https://github.com/mliarakos")),
  pomExtra := {
    <developers>
      <developer>
        <id>mliarakos</id>
        <name>Michael Liarakos</name>
        <url>https://github.com/mliarakos</url>
      </developer>
    </developers>
  },
  pomIncludeRepository := { _ =>
    false
  },
  publishMavenStyle := true,
  publishTo := {
    val nexus = "https://oss.sonatype.org"
    if (isSnapshot.value) Some("snapshots".at(s"$nexus/content/repositories/snapshots"))
    else Some("releases".at(s"$nexus/service/local/staging/deploy/maven2"))
  },
  scmInfo := Some(
    ScmInfo(url("https://github.com/mliarakos/lagom-js"), "scm:git:git@github.com:mliarakos/lagom-js.git")
  )
)

lazy val commonSettings = scalaSettings ++ publishSettings ++ Seq(
  organization := "com.github.mliarakos.lagomjs",
  version := s"0.2.0-$baseLagomVersion-SNAPSHOT"
)

lazy val commonJsSettings = Seq(
  scalacOptions += "-P:scalajs:sjsDefinedByDefault"
)

lazy val lagomVersion         = settingKey[String]("The Lagom version to use.")
lazy val lagomTargetDirectory = settingKey[File]("Directory for the Lagom source.")

lazy val assembleLagomLibrary = taskKey[Unit]("Assemble the library from the Lagom source and then apply overrides.")
lazy val cleanLagomLibrary    = taskKey[Unit]("Remove the Lagom source.")

// Adapted the Git checkout approach used by Akka.js to cross-compile Lagom for JS
// https://github.com/akka-js/akka.js

@inline
def isFileOverride(base: File, target: File): Boolean =
  base.isFile && (target.exists && target.isFile)

@inline
def isDirectoryOverride(base: File, target: File): Boolean =
  base.isDirectory && target.isDirectory && IO.listFiles(target).forall(_.getName.startsWith("."))

def applyOverrides(base: File, target: File): Unit = {
  if (base.exists) {
    if (isFileOverride(base, target) || isDirectoryOverride(base, target))
      IO.delete(base)
    else if (base.isDirectory)
      IO.listFiles(base).foreach(file => applyOverrides(file, target / file.getName))
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
    lagomVersion := baseLagomVersion,
    lagomTargetDirectory := target.value / "lagom-sources" / lagomVersion.value,
    assembleLagomLibrary := {
      checkoutLagomSources(lagomTargetDirectory.value, lagomVersion.value)

      val sourceTarget = file("lagomjs-api") / "shared" / "src" / "main" / "scala"
      copyToSourceDirectory(
        lagomTargetDirectory.value / "service" / "core" / "api" / "src" / "main" / "scala",
        sourceTarget
      )

      val jsSources = sourceDirectory.value / "main" / "scala"
      applyOverrides(sourceTarget, jsSources)
    },
    cleanLagomLibrary := { IO.delete(lagomTargetDirectory.value) }
  )
  .jsSettings(commonJsSettings: _*)
  .jsSettings(
    libraryDependencies ++= Seq(
      "org.akka-js"            %%% "akkajsactor"              % akkaJsVersion,
      "org.akka-js"            %%% "akkajsactorstream"        % akkaJsVersion,
      "org.scala-lang.modules" %%% "scala-parser-combinators" % "1.1.2",
      "com.typesafe.play"      %%% "play-json"                % "2.8.1"
    )
  )
  .jsSettings(
    compile in Compile := { (compile in Compile).dependsOn(assembleLagomLibrary).value },
    publishLocal := { publishLocal.dependsOn(assembleLagomLibrary).value },
    PgpKeys.publishSigned := { PgpKeys.publishSigned.dependsOn(assembleLagomLibrary).value }
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
    lagomVersion := baseLagomVersion,
    lagomTargetDirectory := target.value / "lagom-sources" / lagomVersion.value,
    assembleLagomLibrary := {
      checkoutLagomSources(lagomTargetDirectory.value, lagomVersion.value)

      val sourceTarget = file("lagomjs-api-scaladsl") / "shared" / "src" / "main" / "scala"
      copyToSourceDirectory(
        lagomTargetDirectory.value / "service" / "scaladsl" / "api" / "src" / "main" / "scala",
        sourceTarget
      )

      val sourceTestTarget = file("lagomjs-api-scaladsl") / "shared" / "src" / "test" / "scala"
      copyToSourceDirectory(
        lagomTargetDirectory.value / "service" / "scaladsl" / "api" / "src" / "test" / "scala",
        sourceTestTarget
      )

      val jsSources = sourceDirectory.value / "main" / "scala"
      applyOverrides(sourceTarget, jsSources)
    },
    cleanLagomLibrary := { IO.delete(lagomTargetDirectory.value) }
  )
  .jsSettings(commonJsSettings: _*)
  .jsSettings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % "provided",
      "org.scalatest"  %%% "scalatest"   % "3.0.8"            % Test
    )
  )
  .jsSettings(
    compile in Compile := { (compile in Compile).dependsOn(assembleLagomLibrary).value },
    publishLocal := { publishLocal.dependsOn(assembleLagomLibrary).value },
    PgpKeys.publishSigned := { PgpKeys.publishSigned.dependsOn(assembleLagomLibrary).value }
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
    lagomVersion := baseLagomVersion,
    lagomTargetDirectory := target.value / "lagom-sources" / lagomVersion.value,
    assembleLagomLibrary := {
      checkoutLagomSources(lagomTargetDirectory.value, lagomVersion.value)

      val sourceTarget = file("lagomjs-client") / "shared" / "src" / "main" / "scala"
      copyToSourceDirectory(
        lagomTargetDirectory.value / "service" / "core" / "client" / "src" / "main" / "scala",
        sourceTarget
      )

      val jsSources = sourceDirectory.value / "main" / "scala"
      applyOverrides(sourceTarget, jsSources)
    },
    cleanLagomLibrary := { IO.delete(lagomTargetDirectory.value) }
  )
  .jsSettings(commonJsSettings: _*)
  .jsSettings(
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "0.9.8"
    )
  )
  .jsSettings(
    compile in Compile := { (compile in Compile).dependsOn(assembleLagomLibrary).value },
    publishLocal := { publishLocal.dependsOn(assembleLagomLibrary).value },
    PgpKeys.publishSigned := { PgpKeys.publishSigned.dependsOn(assembleLagomLibrary).value }
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
    lagomVersion := baseLagomVersion,
    lagomTargetDirectory := target.value / "lagom-sources" / lagomVersion.value,
    assembleLagomLibrary := {
      checkoutLagomSources(lagomTargetDirectory.value, lagomVersion.value)

      val sourceTarget = file("lagomjs-client-scaladsl") / "shared" / "src" / "main" / "scala"
      copyToSourceDirectory(
        lagomTargetDirectory.value / "service" / "scaladsl" / "client" / "src" / "main" / "scala",
        sourceTarget
      )

      val sourceTestTarget = file("lagomjs-client-scaladsl") / "shared" / "src" / "test" / "scala"
      copyToSourceDirectory(
        lagomTargetDirectory.value / "service" / "scaladsl" / "client" / "src" / "test" / "scala",
        sourceTestTarget
      )

      val jsSources = sourceDirectory.value / "main" / "scala"
      applyOverrides(sourceTarget, jsSources)
    },
    cleanLagomLibrary := { IO.delete(lagomTargetDirectory.value) }
  )
  .jsSettings(commonJsSettings: _*)
  .jsSettings(
    libraryDependencies ++= Seq(
      "org.scalatest" %%% "scalatest" % "3.0.8" % Test
    )
  )
  .jsSettings(
    compile in Compile := { (compile in Compile).dependsOn(assembleLagomLibrary).value },
    publishLocal := { publishLocal.dependsOn(assembleLagomLibrary).value },
    PgpKeys.publishSigned := { PgpKeys.publishSigned.dependsOn(assembleLagomLibrary).value }
  )
  .jsConfigure(
    _.dependsOn(`lagomjs-client`.js, `lagomjs-api-scaladsl`.js, `lagomjs-macro-testkit`.js % Test)
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
    lagomVersion := baseLagomVersion,
    lagomTargetDirectory := target.value / "lagom-sources" / lagomVersion.value,
    assembleLagomLibrary := {
      checkoutLagomSources(lagomTargetDirectory.value, lagomVersion.value)

      val sourceTarget = file("lagomjs-persistence-scaladsl") / "shared" / "src" / "main" / "scala"
      copyToSourceDirectory(
        lagomTargetDirectory.value / "persistence" / "scaladsl" / "src" / "main" / "scala",
        sourceTarget
      )

      val jsSources = sourceDirectory.value / "main" / "scala"
      applyOverrides(sourceTarget, jsSources)
    },
    cleanLagomLibrary := { IO.delete(lagomTargetDirectory.value) }
  )
  .jsSettings(commonJsSettings: _*)
  .jsSettings(
    compile in Compile := { (compile in Compile).dependsOn(assembleLagomLibrary).value },
    publishLocal := { publishLocal.dependsOn(assembleLagomLibrary).value },
    PgpKeys.publishSigned := { PgpKeys.publishSigned.dependsOn(assembleLagomLibrary).value }
  )
  .jsConfigure(
    _.dependsOn(`lagomjs-api-scaladsl`.js)
  )

lazy val `lagomjs-macro-testkit` = crossProject(JSPlatform)
  .withoutSuffixFor(JSPlatform)
  .crossType(CrossType.Full)
  .in(file("lagomjs-macro-testkit"))
  .settings(commonSettings: _*)
  .settings(
    lagomVersion := baseLagomVersion,
    lagomTargetDirectory := target.value / "lagom-sources" / lagomVersion.value,
    assembleLagomLibrary := {
      checkoutLagomSources(lagomTargetDirectory.value, lagomVersion.value)

      val sourceTarget = file("lagomjs-macro-testkit") / "shared" / "src" / "main" / "scala"
      copyToSourceDirectory(
        lagomTargetDirectory.value / "macro-testkit" / "src" / "main" / "scala",
        sourceTarget
      )

      val jsSources = sourceDirectory.value / "main" / "scala"
      applyOverrides(sourceTarget, jsSources)
    },
    cleanLagomLibrary := { IO.delete(lagomTargetDirectory.value) }
  )
  .settings(
    publish / skip := true,
    publishLocal / skip := true
  )
  .jsSettings(commonJsSettings: _*)
  .jsSettings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % "provided"
    )
  )
  .jsSettings(
    compile in Compile := { (compile in Compile).dependsOn(assembleLagomLibrary).value },
    publishLocal := { publishLocal.dependsOn(assembleLagomLibrary).value },
    PgpKeys.publishSigned := { PgpKeys.publishSigned.dependsOn(assembleLagomLibrary).value }
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
    `lagomjs-persistence-scaladsl`.js,
    `lagomjs-macro-testkit`.js
  )
