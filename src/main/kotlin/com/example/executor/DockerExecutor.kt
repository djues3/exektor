package com.example.executor

import com.example.DockerClientProvider
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.StreamType
import com.github.dockerjava.api.model.WaitResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


const val BILLION: Double = 1_000_000_000.0
/**
 * Executor implementation that runs commands in local Docker containers
 */
class DockerExecutor(
    private val imageName: String = "alpine:latest"
) : Executor {

    private val logger = LoggerFactory.getLogger(DockerExecutor::class.java)
    private val dockerClient = DockerClientProvider.client

    override suspend fun execute(
        executionId: String,
        script: String,
        cpus: Double,
        memoryMb: Int
    ): ExecutionResult {
        logger.info("Starting execution $executionId")

        // Some basic sandboxing
        val hostConfig = HostConfig.newHostConfig()
            .withNanoCPUs((cpus * BILLION).toLong())
            .withMemory((memoryMb * 1024 * 1024).toLong())
            .withNetworkMode("none")
            .withPidsLimit(256)
            .withReadonlyRootfs(true)
            .withSecurityOpts(listOf("no-new-privileges"))

        val container = withContext(Dispatchers.IO) {
            dockerClient.createContainerCmd(imageName)
                .withCmd("/bin/sh", "-c", script)
                .withHostConfig(hostConfig)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withNetworkDisabled(true)
                .exec()
        }

        val containerId = container.id
        logger.info("Created container $containerId for execution $executionId")

        try {
            withContext(Dispatchers.IO) {
                dockerClient.startContainerCmd(containerId).exec()
            }
            logger.info("Started container $containerId")

            val (stdout, stderr) = captureContainerLogs(containerId)
            val exitCode = awaitContainerExit(containerId)

            logger.info(
                "Container $containerId finished with exit code $exitCode"
            )

            return ExecutionResult(exitCode, stdout, stderr)
        } finally {
            try {
                withContext(Dispatchers.IO + NonCancellable) {
                    dockerClient.removeContainerCmd(containerId).exec()
                }
                logger.info("Removed container $containerId")
            } catch (e: Exception) {
                logger.warn("Failed to remove container $containerId", e)
            }
        }
    }


    private suspend fun captureContainerLogs(
        containerId: String
    ): Pair<String, String> {
        val stdoutStream = ByteArrayOutputStream()
        val stderrStream = ByteArrayOutputStream()

        suspendCancellableCoroutine { continuation ->
            val callback = dockerClient.logContainerCmd(containerId)
                .withStdOut(true)
                .withStdErr(true)
                .withFollowStream(true)
                .exec(object : ResultCallback.Adapter<Frame>() {
                    override fun onNext(frame: Frame) {
                        when (frame.streamType) {
                            StreamType.STDOUT ->
                                stdoutStream.write(frame.payload)
                            StreamType.STDERR ->
                                stderrStream.write(frame.payload)
                            else -> {}
                        }
                    }

                    override fun onComplete() {
                        super.onComplete()
                        continuation.resume(Unit)
                    }

                    override fun onError(throwable: Throwable) {
                        super.onError(throwable)
                        continuation.resumeWithException(throwable)
                    }
                })

            continuation.invokeOnCancellation { callback.close() }
        }

        return stdoutStream.toString(Charsets.UTF_8) to
                stderrStream.toString(Charsets.UTF_8)
    }

    private suspend fun awaitContainerExit(
        containerId: String
    ): Int {
        return suspendCancellableCoroutine { continuation ->
            val callback = dockerClient.waitContainerCmd(containerId)
                .exec(object : ResultCallback.Adapter<WaitResponse>() {
                    override fun onNext(response: WaitResponse) {
                        continuation.resume(response.statusCode)
                    }

                    override fun onError(throwable: Throwable) {
                        super.onError(throwable)
                        continuation.resumeWithException(throwable)
                    }
                })

            continuation.invokeOnCancellation { callback.close() }
        }
    }
}


