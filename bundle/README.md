# Camunda Connectors Bundle

The Connectors Bundle contains all out-of-the-box Connectors for Camunda. It's an easy way to try them out in your local setup or in k8s.

The [`Dockerfile`](./default-bundle/Dockerfile) provides an image including the [Connector Runtime]
and all [out-of-the-box Connectors](https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/available-connectors-overview/)
provided by Camunda. The image starts the Connector Runtime with all `jar`
files provided in the `/opt/app` directory as classpath.

To add more connectors to the image, follow the examples in the [Connector Runtime](../connector-runtime/README.md#adding-connectors).

# Docker Compose

The Connectors Bundle is also part of the Camunda [docker-compose resources](https://github.com/camunda/camunda-platform) Docker Compose release.

# Secrets

To inject secrets into the Connector Runtime, they have to be available its environment.

For example, you can inject secrets when running it in a Docker container:

```bash
docker run --rm --name=connectors -d \
           -v $PWD/connector.jar:/opt/app/ \  # Add a connector jar to the classpath
           -e MY_SECRET=secret \              # Set a secret with value
           -e SECRET_FROM_SHELL \             # Set a secret from the environment
           --env-file secrets.txt \           # Set secrets from a file
           camunda/connectors-bundle:8.3.0
```

The secret `MY_SECRET` value is specified directly in the `docker run` call,
whereas the `SECRET_FROM_SHELL` is injected based on the value in the
current shell environment when `docker run` is executed. The `--env-file`
option allows using a single file with the format `NAME=VALUE` per line
to inject multiple secrets at once.

Find further instructions in the [Connector Runtime].

# Build

```bash
docker build -t camunda/connectors-bundle:${VERSION} .
```
