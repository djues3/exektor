# exektor

Exektor is a simple backend service that allows executing shell commands on a remote executor.
It has an API which supports starting jobs, retrieving their status, and retrieving their output via SSE.
The only executor supported right now is Docker (and only with a single daemon).
It uses `docker-java` to communicate with the Docker daemon. `docker-java` does not support using SSH for communication,
so either TCP or Unix sockets are required. 

The API is available at the [following URL](https://executor.djues3.com) but it
requires an API key.

## Testing
There are no tests written yet. The reason being that I could not find a way to test this that doesn't require access to
the DOcker daemon and is then effectively an E2E test. For such a simple project I see no reason to write E2E tests when
manual testing is enough for a project of this size. 

I _could_ test the `ExecutionService` class using a mock `Executor` but I feel like
that would effectively be testing `Kotlinx.*` instead of my own logic since it's that simple. 


---- 

This project was created using the [Ktor Project Generator](https://start.ktor.io).

Here are some useful links to get you started:

- [Ktor Documentation](https://ktor.io/docs/home.html)
- [Ktor GitHub page](https://github.com/ktorio/ktor)
- The [Ktor Slack chat](https://app.slack.com/client/T09229ZC6/C0A974TJ9). You'll need
  to [request an invite](https://surveys.jetbrains.com/s3/kotlin-slack-sign-up) to join.

## Features

Here's a list of features included in this project:

| Name                                                                   | Description                                                                        |
|------------------------------------------------------------------------|------------------------------------------------------------------------------------|
| [Forwarded Headers](https://start.ktor.io/p/forwarded-header-support)  | Allows handling proxied headers (X-Forwarded-*)                                    |
| [Routing](https://start.ktor.io/p/routing)                             | Provides a structured routing DSL                                                  |
| [Content Negotiation](https://start.ktor.io/p/content-negotiation)     | Provides automatic content conversion according to Content-Type and Accept headers |
| [kotlinx.serialization](https://start.ktor.io/p/kotlinx-serialization) | Handles JSON serialization using kotlinx.serialization library                     |
| [Exposed](https://start.ktor.io/p/exposed)                             | Adds Exposed database to your application                                          |

## Building & Running

To build or run the project, use one of the following tasks:

| Task                                    | Description                                                          |
|-----------------------------------------|----------------------------------------------------------------------|
| `./gradlew test`                        | Run the tests                                                        |
| `./gradlew build`                       | Build everything                                                     |
| `./gradlew buildFatJar`                 | Build an executable JAR of the server with all dependencies included |
| `./gradlew buildImage`                  | Build the docker image to use with the fat JAR                       |
| `./gradlew publishImageToLocalRegistry` | Publish the docker image locally                                     |
| `./gradlew run`                         | Run the server                                                       |
| `./gradlew runDocker`                   | Run using the local docker image                                     |

If the server starts successfully, you'll see the following output:

```
2024-12-04 14:32:45.584 [main] INFO  Application - Application started in 0.303 seconds.
2024-12-04 14:32:45.682 [main] INFO  Application - Responding at http://0.0.0.0:8080
```

