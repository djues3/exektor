package com.example.executor

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import java.util.*
import kotlin.time.Instant

/**
 * Represents the result of a command execution
 */
data class ExecutionResult(
    val exitCode: Int, val stdout: String, val stderr: String
)

/**
 * Represents a log event during execution
 */
@Serializable
sealed class LogEvent {
    @Serializable
    data class StdOut(val content: String) : LogEvent()

    @Serializable
    data class StdErr(val content: String) : LogEvent()

    @Serializable
    data class Exit(val code: Int) : LogEvent()
}


/**
 * Interface defining methods for executing scripts with resource constraints.
 */
interface Executor {
    /**
     * Execute a script with the specified resource constraints.
     *
     * @param executionId Unique identifier for this execution
     * @param script The command/script to execute
     * @param cpus Number of CPUs to allocate (fractional values allowed, e.g., 0.5 for half a CPU)
     * @param memoryMb Memory in megabytes to allocate
     * @param onStart Callback invoked when execution starts (can be used to track execution progress)
     * @return ExecutionResult containing exit code, stdout, and stderr
     * @throws Exception if execution fails
     */
    suspend fun execute(
        executionId: UUID, script: String, cpus: Double = 1.0, memoryMb: Int = 512, onStart: suspend () -> Unit = {}
    ): ExecutionResult

    /**
     * Execute a script and stream logs as they arrive.
     * 
     * @param executionId Unique identifier for this execution
     * @param script The command/script to execute
     * @param cpus Number of CPUs to allocate
     * @param memoryMb Memory in megabytes to allocate
     * @return Flow of LogEvent items (stdout, stderr, and final exit code)
     */
    fun executeWithLogs(
        executionId: UUID, script: String, cpus: Double = 1.0, memoryMb: Int = 512, onStart: suspend () -> Unit = {}
    ): Flow<LogEvent>

    /**
     * Stream logs for an already-running execution.
     * Returns null if the execution is unknown or logs are no longer
     * available.
     *
     * @param executionId The execution to stream logs for
     * @param since Only return logs after this instant (null = from
     *   the beginning)
     */
    fun streamLogs(
        executionId: UUID,
        since: Instant? = null,
    ): Flow<LogEvent>?
}
