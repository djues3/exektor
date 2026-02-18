package com.example

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.apikey.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.sse.*
import kotlinx.serialization.json.Json

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    val dockerHost =
        System.getenv("EXEKTOR_DOCKER_HOST") ?: environment.config.propertyOrNull("exektor.docker.host")?.getString()
    DockerClientProvider.initialize(dockerHost)

    install(ContentNegotiation) {
        json(Json {
            isLenient = true
            explicitNulls = false
        })
    }

    install(SSE)

    install(CallLogging)
    install(Authentication) {
        val expectedApiKey =
            System.getenv("EXEKTOR_API_KEY") ?: this@module.environment.config.propertyOrNull("exektor.api.key")
                ?.getString() ?: error("Missing api key configuration")

        data class AppPrincipal(val key: String)

        apiKey {
            headerName = "X-Api-Key"
            validate { key ->
                key.takeIf { it == expectedApiKey }?.let { AppPrincipal(it) }
            }
            challenge { call ->
                call.respond(HttpStatusCode.Unauthorized, "API key is missing or invalid")

            }
        }
    }

    configureHTTP()
    configureDatabases()
    configureRouting()

}
