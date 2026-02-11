package com.example.executor

import kotlinx.serialization.Serializable

@Serializable
data class ExecutionResponse(
    val id: String,
    val script: String,
    val cpus: Double,
    val memoryMb: Int,
    val status: ExecutionStatus,
    val executorId: String? = null,
    val exitCode: Int? = null,
    val stdout: String? = null,
    val stderr: String? = null,
    val createdAt: String,
    val startedAt: String? = null,
    val finishedAt: String? = null
)
