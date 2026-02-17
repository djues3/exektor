package com.example.executor

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.sse.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.util.*


@Serializable
data class CreateExecutionResponse(val id: String, val status: String = "QUEUED")

@Serializable
data class CreateExecutionRequest(
    val script: String, val cpus: Double = 1.0, val memoryMb: Int = 512
)

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
                val id = try {
                    UUID.fromString(
                        call.parameters["id"] ?: return@get call.respond(
                            HttpStatusCode.BadRequest, mapOf("error" to "Missing execution ID")
                        )
                    )
                } catch (_: Exception) {
                    return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid execution ID"))
                }

                val execution = executionService.getExecution(id)
                if (execution == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Execution not found"))
                } else {
                    call.respond(execution)
                }
            }

            get("/executions") {
                val executions = executionService.getAllExecutions()
                call.respond(executions)
            }
        }
        sse("/executions/{id}/stream", serialize = { typeInfo, it ->
            val serializer = Json.serializersModule.serializer(typeInfo.kotlinType!!)
            Json.encodeToString(serializer, it)
        }) {
            val id: UUID = try {
                UUID.fromString(call.parameters["id"])
            } catch (_: Exception) {
                send(data = mapOf("error" to "Invalid execution ID"), event = "error")
                return@sse
            }

            val flow = executionService.streamExecution(id)
            if (flow == null) {
                val execution = executionService.getExecution(id)
                if (execution == null) {
                    send(data = mapOf("error" to "Execution not found"), event = "error")
                } else {
                    send(data = mapOf("error" to "Execution already completed or not started"), event = "error")
                }
                return@sse
            }

            try {
                log.info("Starting SSE stream")
                flow.collect { event ->
                    when (event) {
                        is LogEvent.StdOut -> send(
                            data = mapOf("content" to event.content), event = "stdout"
                        )

                        is LogEvent.StdErr -> send(
                            data = mapOf("content" to event.content), event = "stderr"
                        )

                        is LogEvent.Exit -> send(
                            data = mapOf("code" to event.code), event = "exit"
                        )
                    }
                }
            } catch (e: Exception) {
                send(ServerSentEvent("""{"error": "${e.message}"}""", event = "error"))
            }

        }
    }
}