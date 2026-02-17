package com.example

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.apikey.apiKey
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.sse.SSE
import kotlinx.serialization.json.Json

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    val dockerHost = environment.config.propertyOrNull("exektor.docker.host")?.getString()
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
                ?.getString()
            ?: error("Missing api key configuration")

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
