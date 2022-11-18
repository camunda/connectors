# Camunda 8 Connectors Bundle

The Connectors Bundle contains all out-of-the-box Connectors for Camunda 8. It's an easy way to try them out in your local setup or in k8s.

The bundle contains the following components

| Component                    | Version | License                                      |
| ---------------------------- |---------| -------------------------------------------- |
| [Connector Runtime]          | 0.3.1   | [Apache 2.0]                                 |
| [AWS Lambda Connector]       | 0.4.0   | [Camunda Platform Self-Managed Free Edition] |
| [Google Drive Connector]     | 0.5.0   | [Camunda Platform Self-Managed Free Edition] |
| [HTTP JSON Connector (REST)] | 0.11.0  | [Apache 2.0]                                 |
| [RabbitMQ Connector]         | 0.1.0   | [Camunda Platform Self-Managed Free Edition] |
| [SendGrid Connector]         | 0.13.0  | [Camunda Platform Self-Managed Free Edition] |
| [Slack Connector]            | 0.6.0   | [Camunda Platform Self-Managed Free Edition] |
| [SNS Connector]              | 0.1.1   | [Camunda Platform Self-Managed Free Edition] |
| [SQS Connector]              | 0.3.0   | [Camunda Platform Self-Managed Free Edition] |

The [`Dockerfile`](./Dockerfile) provides an image including the [Connector runtime]
and all [out-of-the-box Connectors](https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/available-connectors-overview/)
provided by Camunda. The image starts the Connector runtime with all `jar`
files provided in the `/opt/app` directory as classpath.

To add more connectors to the image, follow the examples in the [Connector Runtime].

# Secrets

To inject secrets into the docker container during runtime, they have to be
available in the environment of the docker container.

For example, you can inject secrets when running a container:

```bash
docker run --rm --name=connectors -d \
           -v $PWD/connector.jar:/opt/app/ \  # Add a connector jar to the classpath
           -e MY_SECRET=secret \              # Set a secret with value
           -e SECRET_FROM_SHELL \             # Set a secret from the environment
           --env-file secrets.txt \           # Set secrets from a file
           camunda/connectors-bundle:0.3.0
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
docker build \
         --build-arg RUNTIME_VERSION=8.1.6 \        # Overwrite Connector runtime version
         --build-arg SLACK_VERSION=0.5.0  \         # Overwrite Slack Connector version
         -t camunda/connectors-bundle:${VERSION} .
```

# License

[Apache 2.0]

The docker image contains Connectors licensed under [Camunda Platform Self-Managed Free Edition] license.

[apache 2.0]: https://www.apache.org/licenses/LICENSE-2.0
[aws lambda connector]: https://github.com/camunda/connector-aws-lambda
[camunda platform self-managed free edition]: https://camunda.com/legal/terms/cloud-terms-and-conditions/camunda-cloud-self-managed-free-edition-terms/
[google drive connector]: https://github.com/camunda/connector-google-drive
[http json connector (rest)]: https://github.com/camunda/connector-http-json
[rabbitmq connector]: https://github.com/camunda/connector-rabbitmq
[connector runtime]: https://github.com/camunda/connectors-bundle/tree/main/runtime
[sendgrid connector]: https://github.com/camunda/connector-sendgrid
[slack connector]: https://github.com/camunda/connector-slack
[sns connector]: https://github.com/camunda/connector-sns
[sqs connector]: https://github.com/camunda/connector-sqs
