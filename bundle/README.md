# Camunda 8 Connectors Bundle

The Connectors Bundle contains all out-of-the-box Connectors for Camunda 8. It's an easy way to try them out in your local setup or in k8s.

The bundle contains the following components

| Component                    | Version | License                                      |
|------------------------------|---------|----------------------------------------------|
| [Connector Runtime]          | 0.5.0   | [Apache 2.0]                                 |
| [AWS Lambda Connector]       | 0.15.0  | [Camunda Platform Self-Managed Free Edition] |
| [Google Drive Connector]     | 0.15.0  | [Camunda Platform Self-Managed Free Edition] |
| [HTTP JSON Connector (REST)] | 0.15.0  | [Apache 2.0]                                 |
| [RabbitMQ Connector]         | 0.15.0  | [Camunda Platform Self-Managed Free Edition] |
| [SendGrid Connector]         | 0.15.0  | [Camunda Platform Self-Managed Free Edition] |
| [Slack Connector]            | 0.15.0  | [Camunda Platform Self-Managed Free Edition] |
| [SNS Connector]              | 0.15.0  | [Camunda Platform Self-Managed Free Edition] |
| [SQS Connector]              | 0.15.0  | [Camunda Platform Self-Managed Free Edition] |

The [`Dockerfile`](./mvn/default-bundle/Dockerfile) provides an image including the [Connector Runtime]
and all [out-of-the-box Connectors](https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/available-connectors-overview/)
provided by Camunda. The image starts the Connector Runtime with all `jar`
files provided in the `/opt/app` directory as classpath.

To add more connectors to the image, follow the examples in the [Connector Runtime].

# Docker Compose

The Connectors Bundle is also part of the Camunda Platform 8 [docker-compose resources](https://github.com/camunda/camunda-platform).

# Secrets

To inject secrets into the Connector Runtime, they have to be available its environment.

For example, you can inject secrets when running it in a Docker container:

```bash
docker run --rm --name=connectors -d \
           -v $PWD/connector.jar:/opt/app/ \  # Add a connector jar to the classpath
           -e MY_SECRET=secret \              # Set a secret with value
           -e SECRET_FROM_SHELL \             # Set a secret from the environment
           --env-file secrets.txt \           # Set secrets from a file
           camunda/connectors-bundle:0.15.0
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

# License

[Apache 2.0]

The docker image contains Connectors licensed under [Camunda Platform Self-Managed Free Edition] license.

[apache 2.0]: https://www.apache.org/licenses/LICENSE-2.0
[aws lambda connector]: https://github.com/camunda/connectors-bundle/tree/main/connectors/aws-lambda
[camunda platform self-managed free edition]: https://camunda.com/legal/terms/cloud-terms-and-conditions/camunda-cloud-self-managed-free-edition-terms/
[google drive connector]: https://github.com/camunda/connectors-bundle/tree/main/connectors/google-drive
[http json connector (rest)]: https://github.com/camunda/connectors-bundle/tree/main/connectors/http-json
[rabbitmq connector]: https://github.com/camunda/connectors-bundle/tree/main/connectors/rabbit-mq
[connector runtime]: https://github.com/camunda/connector-runtime-docker
[sendgrid connector]: https://github.com/camunda/connectors-bundle/tree/main/connectors/sendgrid
[slack connector]: https://github.com/camunda/connectors-bundle/tree/main/connectors/slack
[sns connector]: https://github.com/camunda/connectors-bundle/tree/main/connectors/sns
[sqs connector]: https://github.com/camunda/connectors-bundle/tree/main/connectors/sqs
