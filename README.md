# Scala.js Client for Lagom

This project is a work in progress. You'll need to compile and locally publish the project to test it out.

### Compatibility

| Lagom.js | Lagom | Scala           | Scala.js |
|----------|-------|-----------------|----------|
| 0.1.0    | 1.5.x | 2.11 <br> 2.12  | 0.6.28+  |

### Usage

Configure the service API as a JVM and JS cross project, then add the Lagom.js API dependency to the JS platform: 

```scala
val lagomjsScaladslApi = "com.github.mliarakos.lagomjs" %%% "lagomjs-scaladsl-api" % "0.1.0-SNAPSHOT"

lazy val `service-api` = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .jvmSettings(
    libraryDependencies += lagomScaladslApi
  )
  .jsSettings(
    libraryDependencies += lagomjsScaladslApi
  )
```

A basic JS client project will depend on the service API JS platform and the Lagom.js client dependency:

```scala
val lagomjsScaladslClient = "com.github.mliarakos.lagomjs" %%% "lagomjs-scaladsl-client" % "0.1.0-SNAPSHOT"

lazy val `client-js` = project
  .settings(
    libraryDependencies += lagomjsScaladslClient
  )
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(`service-api`.js)
```
