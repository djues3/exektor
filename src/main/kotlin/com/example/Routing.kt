package com.example

import com.example.executor.executorRoutes
import io.ktor.server.application.*


fun Application.configureRouting() {
    executorRoutes()
}
