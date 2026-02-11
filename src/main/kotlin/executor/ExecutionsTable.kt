package com.example.executor

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object ExecutionsTable : Table("executions") {
    val id = varchar("id", 36)
    val script = text("script")
    val cpuCount = integer("cpu_count")
    val memoryMb = integer("memory_mb")
    val status = enumerationByName("status", 20, ExecutionStatus::class)
    val executorId = varchar("executor_id", 255).nullable()
    val exitCode = integer("exit_code").nullable()
    val stdout = text("stdout").nullable()
    val stderr = text("stderr").nullable()
    val createdAt = timestamp("created_at")
    val startedAt = timestamp("started_at").nullable()
    val finishedAt = timestamp("finished_at").nullable()

    override val primaryKey = PrimaryKey(id)
}