package com.example

import com.example.models.CreateExecutionRequest
import com.example.service.ExecutionService
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*

@Serializable
data class CreateExecutionResponse(val id: String, val status: String = "QUEUED")

fun Application.configureRouting() {
    val executionService = ExecutionService()

    routing {
        get("/") {
            call.respondText("Exektor - Remote Command Execution Service")
        }

        post("/executions") {
            val request = call.receive<CreateExecutionRequest>()
            val executionId = executionService.createExecution(
                script = request.script,
                cpuCount = request.cpuCount,
                memoryMb = request.memoryMb
            )
            call.respond(HttpStatusCode.Created, CreateExecutionResponse(id = executionId))
        }

        get("/executions/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Missing execution ID")
            )

            val execution = executionService.getExecution(id)
            if (execution == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Execution not found"))
            } else {
                call.respond(execution)
            }
        }
    }
}
