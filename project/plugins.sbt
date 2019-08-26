addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "0.6.0")
addSbtPlugin("org.scala-js"       % "sbt-scalajs"              % "0.6.28")

addSbtPlugin("com.jsuereth"  % "sbt-pgp"      % "1.1.1")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.0.2")

libraryDependencies ++= Seq(
  "org.eclipse.jgit" % "org.eclipse.jgit.pgm" % "3.7.1.201504261725-r"
)
