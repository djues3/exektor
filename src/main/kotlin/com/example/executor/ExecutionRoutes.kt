package com.example.executor

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.*


@Serializable
data class CreateExecutionResponse(val id: String, val status: String = "QUEUED")

fun Application.executorRoutes() {
    val executionService = ExecutionService(executor = DockerExecutor())
    routing {
        get("/") {
            call.respondText("Exektor - Remote Command Execution Service")
        }

        authenticate {
            post("/executions") {
                val request = call.receive<CreateExecutionRequest>()
                val executionId = executionService.createExecution(
                    script = request.script, cpus = request.cpus, memoryMb = request.memoryMb
                )
                call.respond(HttpStatusCode.Created, CreateExecutionResponse(id = executionId.toString()))
            }


            get("/executions/{id}") {
                val id: UUID = UUID.fromString(
                    call.parameters["id"] ?: return@get call.respond(
                        HttpStatusCode.BadRequest, mapOf("error" to "Missing execution ID")
                    )
                )

                val execution = executionService.getExecution(id)
                if (execution == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Execution not found"))
                } else {
                    call.respond(execution)
                }
            }

            get("/executions/") {
                val executions = executionService.getAllExecutions()
                call.respond(executions)
            }
        }
    }
}