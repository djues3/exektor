package com.example.executor

import com.example.DockerClientProvider
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.StreamType
import com.github.dockerjava.api.model.WaitResponse
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import org.slf4j.LoggerFactory
import java.nio.channels.ClosedByInterruptException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.Instant


const val BILLION: Double = 1_000_000_000.0

/**
 * Executor implementation that runs commands in local Docker containers
 */
class DockerExecutor(
    private val imageName: String = "alpine:latest",
    private val executionTimeout: Duration = 60.seconds,
    private val stopTimeout: Duration = 10.seconds,
) : Executor {

    private val logger = LoggerFactory.getLogger(DockerExecutor::class.java)
    private val dockerClient = DockerClientProvider.client
    private val executions = ConcurrentHashMap<UUID, String>()

    override fun executeWithLogs(
        executionId: UUID,
        script: String,
        cpus: Double,
        memoryMb: Int,
        onStart: suspend () -> Unit,
    ): Flow<LogEvent> = channelFlow {

        logger.info("Starting execution $executionId")

        val containerId = createContainer(executionId, script, cpus, memoryMb)
        executions[executionId] = containerId
        var waitCallback: ResultCallback<*>? = null
        try {
            startContainer(containerId)
            onStart()
            val logsJob = launch {
                createLogFlow(containerId).collect { send(it) }
            }
            val exitCode = awaitContainerExit(containerId) { waitCallback = it }

            logsJob.join()

            logger.info("Container $containerId finished with exit code $exitCode")
            send(LogEvent.Exit(exitCode))
        } catch (_: TimeoutCancellationException) {
            logger.warn("Container $containerId timed out after $executionTimeout")
            forceStopContainer(containerId)
            send(LogEvent.StdErr("\nExecution timed out after $executionTimeout\n"))
            send(LogEvent.Exit(-1))
        } finally {
            removeContainer(containerId, this)
            waitCallback?.close()
            executions -= executionId
        }
    }.flowOn(Dispatchers.IO)


    override suspend fun execute(
        executionId: UUID, script: String, cpus: Double, memoryMb: Int, onStart: suspend () -> Unit
    ): ExecutionResult {
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        var exitCode = -1

        try {
            executeWithLogs(executionId, script, cpus, memoryMb, onStart).collect { event ->

                when (event) {
                    is LogEvent.StdOut -> stdout.append(event.content)
                    is LogEvent.StdErr -> stderr.append(event.content)
                    is LogEvent.Exit -> exitCode = event.code
                }
            }
        } catch (e: ClosedByInterruptException) {
            throw e
        }

        return ExecutionResult(exitCode, stdout.toString(), stderr.toString())
    }

    override fun streamLogs(
        executionId: UUID, since: Instant?
    ): Flow<LogEvent>? {
        val since = since ?: Clock.System.now().minus(1.days)

        val containerId = executions[executionId] ?: return null
        logger.info("Streaming logs for execution $executionId ($containerId) since $since")
        return createLogFlow(containerId, since)
            .flowOn(Dispatchers.IO)
    }

    private suspend fun createContainer(
        executionId: UUID,
        script: String,
        cpus: Double,
        memoryMb: Int,
    ): String {
        val hostConfig = HostConfig.newHostConfig().withNanoCPUs(
            // If the user puts in a 0 as `cpus` value, they could turn off limiting, so force some limit.
            // 0.01 was chosen arbitrarily
            (cpus * BILLION).coerceAtLeast(BILLION / 100).toLong()
        ).withMemory(memoryMb * 1024L * 1024L).withNetworkMode("none").withPidsLimit(256).withReadonlyRootfs(true)
            .withSecurityOpts(listOf("no-new-privileges"))

        val container = withContext(Dispatchers.IO) {
            dockerClient.createContainerCmd(imageName).withCmd("/bin/sh", "-c", script).withHostConfig(hostConfig)
                .withAttachStdout(true).withAttachStderr(true).withNetworkDisabled(true).exec()
        }

        logger.info("Created container ${container.id} for execution $executionId")

        return container.id
    }


    private suspend fun startContainer(containerId: String) {
        withContext(Dispatchers.IO) {
            dockerClient.startContainerCmd(containerId).exec()
        }
        logger.info("Started container $containerId")
    }

    private fun createLogFlow(containerId: String, since: Instant? = null): Flow<LogEvent> = callbackFlow {
        val cmd = dockerClient.logContainerCmd(containerId).withStdOut(true).withStdErr(true).withFollowStream(true)
            .withSince((since?.epochSeconds ?: 0).toInt())
        val callback = cmd.exec(object : ResultCallback.Adapter<Frame>() {
            override fun onNext(frame: Frame) {
                val content = String(frame.payload, Charsets.UTF_8)
                when (frame.streamType) {
                    StreamType.STDOUT -> trySend(LogEvent.StdOut(content))
                    StreamType.STDERR -> trySend(LogEvent.StdErr(content))
                    else -> {}
                }
            }

            override fun onError(throwable: Throwable) {
                cancel("Docker log error", throwable)
            }

            override fun onComplete() {
                this@callbackFlow.close()
                super.onComplete()
            }
        })

        awaitClose { callback.close() }
    }

    private suspend fun awaitContainerExit(
        containerId: String, onCallback: (ResultCallback<*>) -> Unit = {}
    ): Int = withTimeout(executionTimeout) {
        suspendCancellableCoroutine { continuation ->
            val callback =
                dockerClient.waitContainerCmd(containerId).exec(object : ResultCallback.Adapter<WaitResponse>() {
                    override fun onNext(response: WaitResponse) {
                        continuation.resume(response.statusCode)
                    }

                    override fun onError(throwable: Throwable) {
                        logger.error("Failed to wait for container $containerId", throwable)
                        continuation.resumeWithException(throwable)
                    }
                })
            onCallback(callback)
            continuation.invokeOnCancellation { callback.close() }
        }
    }

    private suspend fun forceStopContainer(containerId: String) {
        withContext(Dispatchers.IO + NonCancellable) {
            try {
                dockerClient.stopContainerCmd(containerId).withTimeout(stopTimeout.toInt(DurationUnit.SECONDS)).exec()
                println("Stopped container $containerId")
            } catch (e: Exception) {
                logger.error("Failed to stop container $containerId", e)
            }
        }
    }

    private suspend fun removeContainer(containerId: String, scope: ProducerScope<*>) {
        withContext(Dispatchers.IO + NonCancellable) {
            try {
                dockerClient.removeContainerCmd(containerId).withForce(true).exec()
                println("Closing scope: $scope")
                scope.close()
                logger.info("Removed container $containerId")
            } catch (e: Exception) {
                logger.warn("Failed to remove container $containerId", e)
                scope.close(e)
            }
            println("Removed container $containerId")
        }
    }
}
