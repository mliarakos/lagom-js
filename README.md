# Lagom.js - Scala.js Client for Lagom

Lagom.js is a port of the [Lagom](https://www.lagomframework.com/) service API and service client to JavaScript. It allows you to use and interact with your service API all in JavaScript. Eliminate the need to keep your frontend in sync with your service API, let lagom.js handle it for you.

Checkout the [lagom-scalajs-example](https://github.com/mliarakos/lagom-scalajs-example) for a demo of how to use lagom.js.

## Getting Started

This project is a work in progress and currently only has snapshot artifacts. To use the snapshots you'll need to configure SBT to resolve them:

```sbt
resolvers += Resolver.sonatypeRepo("snapshots")
```

### Compatibility

Lagom.js is built against specific versions of Lagom.

| Lagom.js    | Lagom | Scala           | Scala.js |
|-------------|-------|-----------------|----------|
| 0.1.0-1.5.1 | 1.5.1 | 2.11 <br> 2.12  | 0.6.24+  |

## Usage

Lagom.js provides two primary artifacts. The first artifact is `lagomjs-scaladsl-api`:

```sbt
"com.github.mliarakos.lagomjs" %%% "lagomjs-scaladsl-api" % "0.1.0-1.5.1-SNAPSHOT"
```

This provides the JavaScript implementation of the Lagom service API. To use it you'll need to configure your service API
as a JVM and JS cross project. Then, add the `lagomjs-scaladsl-api` dependency to the JS platform: 

```scala
val lagomjsScaladslApi = "com.github.mliarakos.lagomjs" %%% "lagomjs-scaladsl-api" % "0.1.0-1.5.1-SNAPSHOT"

lazy val `service-api` = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .jvmSettings(
    libraryDependencies += lagomScaladslApi
  )
  .jsSettings(
    libraryDependencies += lagomjsScaladslApi
  )
```

The second artifact is `lagomjs-scaladsl-client`:

```sbt
"com.github.mliarakos.lagomjs" %%% "lagomjs-scaladsl-client" % "0.1.0-1.5.1-SNAPSHOT"
```

This provides the JavaScript implementation of the Lagom service client. You can use it in a Scala.js project along with your service API to generate a service client:

```scala
val lagomjsScaladslClient = "com.github.mliarakos.lagomjs" %%% "lagomjs-scaladsl-client" % "0.1.0-1.5.1-SNAPSHOT"

lazy val `client-js` = project
  .settings(
    libraryDependencies += lagomjsScaladslClient
  )
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(`service-api`.js)
```

## Features

Lagom.js supports cross compiling the full Lagom service API into JavaScript. However, not all the features are available in the service client. The service client supports:
- all the service call definitions: `call`, `namedCall`, `pathCall`, `restCall`
- serialization of service requests and responses using `play-json`
- streaming service requests and responses using `akka.js` and WebSockets

The service client does not support:
- circuit breakers: the circuit breaker configuration in the service definition is available, but is ignored and all service calls are invoked without circuit breakers
- subscribing to topics: the topic definition in the service client is available, but attempting to subscribe to the topic throws an exception
- the full range of service locators: the only available `ServiceLocator` implementation is `StaticServiceLocator`
