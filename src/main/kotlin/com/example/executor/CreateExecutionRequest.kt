package com.example.executor

import kotlinx.serialization.Serializable

@Serializable
data class CreateExecutionRequest(
    val script: String,
    val cpus: Double = 1.0,
    val memoryMb: Int = 512
)
