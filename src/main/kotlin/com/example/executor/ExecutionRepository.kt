package com.example.executor

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.util.*

/**
 * Internal model for partial updates
 */
data class ExecutionUpdate(
    val status: ExecutionStatus? = null,
    val executorId: String? = null,
    val exitCode: Int? = null,
    val stdout: String? = null,
    val stderr: String? = null,
    val startedAt: Instant? = null,
    val finishedAt: Instant? = null
)

object ExecutionRepository {
    suspend fun create(script: String, cpus: Double, memoryMb: Int): String = dbQuery {
        val id = UUID.randomUUID().toString()
        ExecutionsTable.insert {
            it[ExecutionsTable.id] = id
            it[ExecutionsTable.script] = script
            it[ExecutionsTable.cpus] = cpus
            it[ExecutionsTable.memoryMb] = memoryMb
            it[ExecutionsTable.status] = ExecutionStatus.QUEUED
            it[createdAt] = Instant.now()
        }
        id
    }

    suspend fun findById(id: String): ExecutionResponse? = dbQuery {
        ExecutionsTable.selectAll()
            .where { ExecutionsTable.id eq id }
            .map { it.toExecutionResponse() }
            .singleOrNull()
    }

    suspend fun update(id: String, update: ExecutionUpdate) = dbQuery {
        ExecutionsTable.update({ ExecutionsTable.id eq id }) {
            update.status?.let { status -> it[ExecutionsTable.status] = status }
            update.executorId?.let { executorId -> it[ExecutionsTable.executorId] = executorId }
            update.exitCode?.let { exitCode -> it[ExecutionsTable.exitCode] = exitCode }
            update.stdout?.let { stdout -> it[ExecutionsTable.stdout] = stdout }
            update.stderr?.let { stderr -> it[ExecutionsTable.stderr] = stderr }
            update.startedAt?.let { startedAt -> it[ExecutionsTable.startedAt] = startedAt }
            update.finishedAt?.let { finishedAt -> it[ExecutionsTable.finishedAt] = finishedAt }
        }
    }

    private fun ResultRow.toExecutionResponse() = ExecutionResponse(
        id = this[ExecutionsTable.id],
        script = this[ExecutionsTable.script],
        cpuCount = this[ExecutionsTable.cpus],
        memoryMb = this[ExecutionsTable.memoryMb],
        status = this[ExecutionsTable.status],
        executorId = this[ExecutionsTable.executorId],
        exitCode = this[ExecutionsTable.exitCode],
        stdout = this[ExecutionsTable.stdout],
        stderr = this[ExecutionsTable.stderr],
        createdAt = this[ExecutionsTable.createdAt].toString(),
        startedAt = this[ExecutionsTable.startedAt]?.toString(),
        finishedAt = this[ExecutionsTable.finishedAt]?.toString()
    )

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
