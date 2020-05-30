package org.mliarakos.lagomjs.it.api

import org.mliarakos.lagomjs.it.api.TestEnvironmentExceptionSerializer.devEnvironment

object TestExceptionSerializer extends TestEnvironmentExceptionSerializer(devEnvironment)
