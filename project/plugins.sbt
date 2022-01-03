addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.0.0")
addSbtPlugin("org.scala-js"       % "sbt-scalajs"              % "1.8.0")

addSbtPlugin("com.jsuereth"  % "sbt-pgp"      % "1.1.2")
addSbtPlugin("org.akka-js"   % "sbt-shocon"   % "1.0.0")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.3.4")

libraryDependencies ++= Seq(
  "org.eclipse.jgit" % "org.eclipse.jgit.pgm"      % "3.7.1.201504261725-r",
  "org.scala-js"     %% "scalajs-env-jsdom-nodejs" % "1.1.0"
)
