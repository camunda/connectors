# Camunda 8 Connectors Bundle

The connectors bundle contains all out of the box connectors for Camunda 8. It's an easy way to try them out in your local setup or in k8s.

The bundle contains the following components

| Component                  | Version |
| -------------------------- | ------- |
| Job Worker Runtime         | 0.2.0   |
| AWS Lambda Connector       | 0.3.0   |
| Google Drive Connector     | 0.4.0   |
| HTTP JSON Connector (REST) | 0.9.0   |
| SendGrid Connector         | 0.11.0  |
| Slack Connector            | 0.4.0   |
| SQS Connector              | 0.2.0   |

The [`Dockerfile`](./Dockerfile) provides an image including the [job worker runtime](https://github.com/camunda/connector-sdk/tree/main/runtime-job-worker)
and all [out-of-the-box Connectors](https://docs.camunda.io/docs/components/integration-framework/connectors/out-of-the-box-connectors/available-connectors-overview/)
provided by Camunda. The image starts the job worker runtime with all `jar`
files provided in the `/opt/app` directory as classpath.

To add more connectors to the image follow the examples in the [Docker Job Worker Runtime Image](https://github.com/camunda/connector-sdk/tree/main/runtime-job-worker#docker-job-worker-runtime-image)
section.

# Secrets

To inject secrets into the docker container during runtime, they have to be
available in the environment of the docker container.

For example, you can inject secrets when running a container:

```bash
docker run --rm --name=connectors -d -e MY_SECRET=secret -e SECRET_FROM_SHELL --env-file secrets.txt camunda/connectors-bundle:0.2.0
```

The secret `MY_SECRET` value is specified directly in the `docker run` call,
whereas the `SECRET_FROM_SHELL` is injected based on the value in the
current shell environment when `docker run` is executed. The `--env-file`
option allows using a single file with the format `NAME=VALUE` per line
to inject multiple secrets at once.

# Build

```bash
docker build -t camunda/connectors-bundle:${VERSION} .
```

All version can be overwritten as build args, i.e. to use a different runtime version and slack connector version run

```bash
docker build --build-arg RUNTIME_VERSION=0.3.0 --build-arg SLACK_VERSION=0.5.0 -t camunda/connectors-bundle:${VERSION} .
```
