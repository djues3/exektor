package com.example.executor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Service that manages the execution lifecycle using pluggable executors
 */
class ExecutionService(
    private val executor: Executor,
    private val repository: ExecutionRepository = ExecutionRepository
) {
    private val logger = LoggerFactory.getLogger(ExecutionService::class.java)
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    suspend fun createExecution(script: String, cpus: Double, memoryMb: Int): String {
        val executionId = repository.create(script, cpus, memoryMb)

        coroutineScope.launch {
            try {
                executeWithBackend(executionId, script, cpus, memoryMb)
            } catch (e: Exception) {
                logger.error("Execution $executionId failed with exception", e)
                repository.update(
                    executionId,
                    ExecutionUpdate(
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

    suspend fun getExecution(id: String): ExecutionResponse? {
        return repository.findById(id)
    }

    private suspend fun executeWithBackend(
        executionId: String,
        script: String,
        cpuCount: Double,
        memoryMb: Int
    ) {
        logger.info("Starting execution $executionId with ${executor::class.simpleName}")

        repository.update(
            executionId,
            ExecutionUpdate(
                status = ExecutionStatus.RUNNING,
                startedAt = Instant.now()
            )
        )

        val result = executor.execute(executionId, script, cpuCount, memoryMb)

        repository.update(
            executionId,
            ExecutionUpdate(
                status = if (result.exitCode == 0) ExecutionStatus.FINISHED else ExecutionStatus.FAILED,
                exitCode = result.exitCode,
                stdout = result.stdout,
                stderr = result.stderr,
                finishedAt = Instant.now()
            )
        )
    }
}
