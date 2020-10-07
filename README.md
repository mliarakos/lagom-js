# Lagom.js - Scala.js Client for Lagom

Lagom.js is a port of the [Lagom](https://www.lagomframework.com/) service API and service client to JavaScript using Scala.js. It allows you to use and interact with your service API all in JavaScript. Eliminate the need to keep your frontend in sync with your service API, let lagom.js handle it for you.

Checkout the [lagom-scalajs-example](https://github.com/mliarakos/lagom-scalajs-example) for a demo of how to use lagom.js.

## Compatibility

Lagom.js is built against specific versions of Lagom, the latest are:

| Lagom.js    | Lagom | Scala           | Scala.js |
|-------------|-------|-----------------|----------|
| 0.1.2-1.5.5 | 1.5.5 | 2.11 <br> 2.12  | 0.6.31   |
| 0.3.2-1.6.2 | 1.6.2 | 2.12 <br> 2.13  | 0.6.33   |
| 0.4.1-1.6.4 | 1.6.4 | 2.12 <br> 2.13  | 1.2.0    |

Lagom.js moved to Scala.js 1.x starting with version `0.4.0-1.6.2`. Scala.js 0.6 is no longer supported, the last version to support it was `0.3.2-1.6.2`. For all past releases, see [releases](#Releases).

Lagom.js does not support the Lagom Java API. It only supports the Lagom Scala API because Scala.js only supports Scala.

## Usage

Lagom.js provides JavaScript versions of several Lagom artifacts. The two most important are the service API and service client.

### Service API

The `lagomjs-scaladsl-api` artifact provides the JavaScript implementation of the Lagom service API:

```sbt
"com.github.mliarakos.lagomjs" %%% "lagomjs-scaladsl-api" % "0.4.1-1.6.4"
```

To use it you'll need to configure your service API as a [Scala.js cross project](https://github.com/portable-scala/sbt-crossproject) for the JVM and JS platforms. Then, add the `lagomjs-scaladsl-api` dependency to the JS platform:

```scala
lazy val `service-api` = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .jvmSettings(
    libraryDependencies += lagomScaladslApi
  )
  .jsSettings(
    libraryDependencies += "com.github.mliarakos.lagomjs" %%% "lagomjs-scaladsl-api" % "0.4.1-1.6.4"
  )
```

This enables your Lagom service definition to be compiled into JavaScript. In addition, your domain objects, service requests and responses, and custom exceptions are also compiled into JavaScript. This makes your entire service API available in JavaScript.

### Service Client

The `lagomjs-scaladsl-client` artifact provides the JavaScript implementation of the Lagom service client:

```sbt
"com.github.mliarakos.lagomjs" %%% "lagomjs-scaladsl-client" % "0.4.1-1.6.4"
```

You can use it in a Scala.js project along with your service API to generate a service client:

```scala
lazy val `client-js` = project
  .settings(
    libraryDependencies += "com.github.mliarakos.lagomjs" %%% "lagomjs-scaladsl-client" % "0.4.1-1.6.4"
  )
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(`service-api`.js)
```

The service client can be used to interact with your service by making service calls, just as you normally would in Scala. Since the entire service API is available in JavaScript you have everything you need to create requests and understand responses.

## Features

Lagom.js supports cross compiling the full Lagom service API into JavaScript. The service client supports almost all features available in Lagom:
- all the service call definitions: `call`, `namedCall`, `pathCall`, `restCall`
- serialization of service requests and responses using `play-json`
- streaming service requests and responses using [Akka.js](https://github.com/akka-js/akka.js) and WebSockets
- circuit breakers with basic metrics
- all the built-in service locators: `ConfigurationServiceLocator`, `StaticServiceLocator` and `RoundRobinServiceLocator`

However, the service client does not support a few the features available in Lagom:
- full circuit breaker metrics: circuit breakers are fully supported, but the built-in circuit breaker metrics implementation only collects a few basic metrics
- subscribing to topics: topic definitions are available in the service client, but attempting to subscribe to the topic throws an exception
- advanced service locators: service locators outside of the built-in service locators, such as `AkkaDiscoveryServiceLocator`, are not available

## Known issues
`ServiceCalls` based on akka-stream `Source` are implemented using Websockets, which don't propagate backpressure. This might change with [WebSocketStream API](https://web.dev/websocketstream/) once it's widely available.

## Releases

Lagom.js tracks Lagom and generally doesn't continue development on previous Lagom releases. Since Lagom maintains a stable API within a minor release (e.g., 1.6.x) any version of Lagom.js built for that minor release should work. However, if you need Lagom.js for a specific previous Lagom release the previous Lagom.js releases are listed below. If you have an issue with a previous Lagom.js release please open an [issue](https://github.com/mliarakos/lagom-js/issues) and it will be considered on a case-by-case basis.

| Lagom.js    | Lagom | Scala           | Scala.js |
|-------------|-------|-----------------|----------|
| 0.1.2-1.5.1 | 1.5.1 | 2.11 <br> 2.12  | 0.6.31   |
| 0.1.2-1.5.3 | 1.5.3 | 2.11 <br> 2.12  | 0.6.31   |
| 0.1.2-1.5.4 | 1.5.4 | 2.11 <br> 2.12  | 0.6.31   |
| 0.1.2-1.5.5 | 1.5.5 | 2.11 <br> 2.12  | 0.6.31   |
| 0.2.1-1.6.0 | 1.6.0 | 2.12            | 0.6.31   |
| 0.3.1-1.6.1 | 1.6.1 | 2.12 <br> 2.13  | 0.6.32   |
| 0.3.2-1.6.2 | 1.6.2 | 2.12 <br> 2.13  | 0.6.33   |
| 0.4.0-1.6.2 | 1.6.2 | 2.12 <br> 2.13  | 1.0.1    |
| 0.4.0-1.6.3 | 1.6.3 | 2.12 <br> 2.13  | 1.1.1    |
| 0.4.0-1.6.4 | 1.6.4 | 2.12 <br> 2.13  | 1.2.0    |
| 0.4.1-1.6.4 | 1.6.4 | 2.12 <br> 2.13  | 1.2.0    |

