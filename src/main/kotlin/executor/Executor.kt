package com.example.executor

/**
 * Represents the result of a command execution
 */
data class ExecutionResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
)

/**
 * Interface for different execution backends (Docker, K8s, SSH, etc.)
 */
interface Executor {
    /**
     * Execute a script with the specified resource constraints.
     *
     * @param executionId Unique identifier for this execution
     * @param script The command/script to execute
     * @param cpuCount Number of CPUs to allocate
     * @param memoryMb Memory in megabytes to allocate
     * @return ExecutionResult containing exit code, stdout, and stderr
     * @throws Exception if execution fails
     */
    suspend fun execute(
        executionId: String,
        script: String,
        cpuCount: Int,
        memoryMb: Int
    ): ExecutionResult
}
