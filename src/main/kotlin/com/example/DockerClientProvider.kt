package com.example

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient

object DockerClientProvider {
    lateinit var client: DockerClient
        private set

    // Initialize the docker client.
    // If `host` is null, the default `DOCKER_HOST` is used.
    fun initialize(host: String? = null) {

        var builder = DefaultDockerClientConfig.createDefaultConfigBuilder()

        if (host != null) {
            builder = builder.withDockerHost(host)
        }
        val config = builder
            .build()

        val httpClient = ApacheDockerHttpClient.Builder()
            .dockerHost(config.dockerHost)
            .sslConfig(config.sslConfig)
            .build()
        client = DockerClientImpl.getInstance(config, httpClient)

    }
}