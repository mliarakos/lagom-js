# Lagom.js - Scala.js Client for Lagom

Lagom.js is a port of the [Lagom](https://www.lagomframework.com/) service API and service client to JavaScript using Scala.js. It allows you to use and interact with your service API all in JavaScript. Eliminate the need to keep your frontend in sync with your service API, let lagom.js handle it for you.

Checkout the [lagom-scalajs-example](https://github.com/mliarakos/lagom-scalajs-example) for a demo of how to use lagom.js.

## Compatibility

Lagom.js is built against specific versions of Lagom:

| Lagom.js    | Lagom | Scala           | Scala.js |
|-------------|-------|-----------------|----------|
| 0.1.0-1.5.1 | 1.5.1 | 2.11 <br> 2.12  | 0.6.24+  |

Lagom.js does not support the Lagom Java API. It only supports the Lagom Scala API because Scala.js only supports Scala.

## Usage

Lagom.js provides JavaScript versions of several Lagom artifacts. The two most important are the service API and service client.

### Service API

The `lagomjs-scaladsl-api` artifact provides the JavaScript implementation of the Lagom service API:

```sbt
"com.github.mliarakos.lagomjs" %%% "lagomjs-scaladsl-api" % "0.1.0-1.5.1"
```

To use it you'll need to configure your service API as a [Scala.js cross project](https://github.com/portable-scala/sbt-crossproject) for the JVM and JS platforms. Then, add the `lagomjs-scaladsl-api` dependency to the JS platform:

```scala
lazy val `service-api` = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .jvmSettings(
    libraryDependencies += lagomScaladslApi
  )
  .jsSettings(
    libraryDependencies += "com.github.mliarakos.lagomjs" %%% "lagomjs-scaladsl-api" % "0.1.0-1.5.1"
  )
```

This enables your Lagom service definition to be compiled into JavaScript. In addition, your domain objects, service requests and responses, and custom exceptions are also compiled into JavaScript. This makes your entire service API available in JavaScript.

### Service Client

The `lagomjs-scaladsl-client` artifact provides the JavaScript implementation of the Lagom service client:

```sbt
"com.github.mliarakos.lagomjs" %%% "lagomjs-scaladsl-client" % "0.1.0-1.5.1"
```

You can use it in a Scala.js project along with your service API to generate a service client:

```scala
lazy val `client-js` = project
  .settings(
    libraryDependencies += "com.github.mliarakos.lagomjs" %%% "lagomjs-scaladsl-client" % "0.1.0-1.5.1"
  )
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(`service-api`.js)
```

The service client can be used to interact with your service by making service calls, just as you normally would in Scala. Since the entire service API is available in JavaScript you have everything you need to create requests and understand responses.

## Features

Lagom.js supports cross compiling the full Lagom service API into JavaScript. However, the service client does not support all the features available in Lagom. The service client supports:
- all the service call definitions: `call`, `namedCall`, `pathCall`, `restCall`
- serialization of service requests and responses using `play-json`
- streaming service requests and responses using `akka.js` and WebSockets

The service client does not support:
- circuit breakers: the circuit breaker configuration in the service definition is available, but is ignored and all service calls are invoked without circuit breakers
- subscribing to topics: the topic definition in the service client is available, but attempting to subscribe to the topic throws an exception
- the full range of service locators: the only available `ServiceLocator` implementations are `StaticServiceLocator` and `RoundRobinServiceLocator`
