package com.example

import io.ktor.server.application.*
import io.ktor.server.plugins.forwardedheaders.*

fun Application.configureHTTP() {
    if (isProduction()) {
        install(ForwardedHeaders) // WARNING: for security, do not include this if not behind a reverse proxy
        install(XForwardedHeaders) // WARNING: for security, do not include this if not behind a reverse proxy
    }
}

fun Application.isProduction() =
    environment.config.propertyOrNull("ktor.deployment.environment")?.getString() == "production"