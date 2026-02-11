package com.example.executor

import kotlinx.serialization.Serializable

@Serializable
data class CreateExecutionRequest(
    val script: String,
    val cpuCount: Int = 1,
    val memoryMb: Int = 512
)
