package com.example.executor

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.*

/**
 * Service that manages the execution lifecycle using pluggable executors
 */
class ExecutionService(
    private val executor: Executor, private val repository: ExecutionRepository = ExecutionRepository
) {
    private val logger = LoggerFactory.getLogger(ExecutionService::class.java)
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    suspend fun createExecution(script: String, cpus: Double, memoryMb: Int): UUID {
        val executionId = repository.create(script, cpus, memoryMb)

        coroutineScope.launch {
            try {
                executeWithBackend(executionId, script, cpus, memoryMb)
            } catch (e: Exception) {
                logger.error("Execution $executionId failed with exception", e)
                repository.update(
                    executionId, ExecutionUpdate(
                        status = ExecutionStatus.FAILED,
                        exitCode = -1,
                        stdout = "",
                        stderr = e.message ?: "Unknown error",
                        finishedAt = Instant.now()
                    )
                )
            }
        }

        return executionId
    }

    suspend fun getExecution(id: UUID): ExecutionResponse? {
        return repository.findById(id)
    }

    fun streamExecution(id: UUID): Flow<LogEvent>? = executor.streamLogs(id)

    suspend fun getAllExecutions(): List<ExecutionResponse> = repository.findAll()

    private suspend fun executeWithBackend(
        executionId: UUID, script: String, cpuCount: Double = 1.0, memoryMb: Int = 512
    ) {
        logger.info("Starting execution $executionId with ${executor::class.simpleName}")


        val result = executor.execute(executionId, script, cpuCount, memoryMb) {
            repository.update(
                executionId, ExecutionUpdate(
                    status = ExecutionStatus.RUNNING, startedAt = Instant.now()
                )
            )
            logger.info("Execution $executionId started")
        }


        repository.update(executionId, update = result.toExecutionUpdate())
    }
}


fun ExecutionResult.toExecutionUpdate(): ExecutionUpdate {
    val status = when (exitCode) {
        0 -> ExecutionStatus.FINISHED
        -1 -> ExecutionStatus.TIMED_OUT
        else -> ExecutionStatus.FAILED
    }

    return ExecutionUpdate(
        status = status, exitCode = exitCode, stdout = stdout, stderr = stderr, finishedAt = Instant.now()
    )
}