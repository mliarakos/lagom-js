val scalaJSVersion = Option(System.getenv("SCALAJS_VERSION")).getOrElse("0.6.33")

addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.0.0")
addSbtPlugin("org.scala-js"       % "sbt-scalajs"              % scalaJSVersion)

addSbtPlugin("com.jsuereth"  % "sbt-pgp"      % "1.1.1")
addSbtPlugin("org.akka-js"   % "sbt-shocon"   % "0.5.0")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.3.0")

libraryDependencies ++= Seq(
  "org.eclipse.jgit" % "org.eclipse.jgit.pgm" % "3.7.1.201504261725-r"
) ++ {
  if (scalaJSVersion.startsWith("0.6.")) Nil
  else Seq("org.scala-js" %% "scalajs-env-jsdom-nodejs" % "1.0.0")
}
