package org.mliarakos.lagomjs.it.api.exception

import org.mliarakos.lagomjs.it.api.exception.TestEnvironmentExceptionSerializer.devEnvironment

object TestExceptionSerializer extends TestEnvironmentExceptionSerializer(devEnvironment)
