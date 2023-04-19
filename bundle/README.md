# Camunda 8 Connectors Bundle

The Connectors Bundle contains all out-of-the-box Connectors for Camunda 8. It's an easy way to try them out in your local setup or in k8s.

The bundle contains the following components

| Component                      | Version | License                                      | 
|--------------------------------|---------|----------------------------------------------|
| [Connector Runtime]            | 0.8.1   | [Apache 2.0]                                 |
| Asana Connector                | 0.18.2  | [Camunda Platform Self-Managed Free Edition] |
| Automation Anywhere Connector  | 0.18.2  | [Camunda Platform Self-Managed Free Edition] |
| Amazon SNS Connector           | 0.18.2  | [Camunda Platform Self-Managed Free Edition] |
| Amazon SQS Connector           | 0.18.2  | [Camunda Platform Self-Managed Free Edition] |
| AWS Lambda Connector           | 0.18.2  | [Camunda Platform Self-Managed Free Edition] |
| Camunda Operate Connector      | 0.18.2  | [Camunda Platform Self-Managed Free Edition] |
| Easy Post Connector            | 0.18.2  | [Camunda Platform Self-Managed Free Edition] |
| GitHub Connector               | 0.18.2  | [Camunda Platform Self-Managed Free Edition] |
| GitHub Webhook Connector       | 0.18.2  | [Camunda Platform Self-Managed Free Edition] |
| GitLab Connector               | 0.18.2  | [Camunda Platform Self-Managed Free Edition] |
| Google Drive Connector         | 0.18.2  | [Camunda Platform Self-Managed Free Edition] |
| Google Maps Platform Connector | 0.18.2  | [Camunda Platform Self-Managed Free Edition] |
| GraphQL Connector              | 0.18.2  | [Camunda Platform Self-Managed Free Edition] |
| HTTP Webhook Connector         | 0.18.2  | [Camunda Platform Self-Managed Free Edition] |
| Kafka Producer Connector       | 0.18.2  | [Camunda Platform Self-Managed Free Edition] |
| Microsoft Teams Connector      | 0.18.2  | [Camunda Platform Self-Managed Free Edition] |
| OpenAI Connector               | 0.18.2  | [Camunda Platform Self-Managed Free Edition] |
| Power Automate Connector       | 0.18.2  | [Camunda Platform Self-Managed Free Edition] |
| RabbitMQ Connector             | 0.18.2  | [Camunda Platform Self-Managed Free Edition] |
| REST Connector                 | 0.18.2  | [Apache 2.0]                                 |
| SendGrid Connector             | 0.18.2  | [Camunda Platform Self-Managed Free Edition] |
| Slack Connector                | 0.18.2  | [Camunda Platform Self-Managed Free Edition] |
| UiPath Connector               | 0.18.2  | [Camunda Platform Self-Managed Free Edition] |

**Note:** 
- This list only includes Camunda 8 out-of-the-box Connectors that have their own implementation.
Some of these Connectors are **Protocol Connectors**, which means they are compatible with more than one element template.
- Some out-of-the-box Connectors in Camunda 8 only exist in the form of element-template for Protocol Connectors.
Such template-only Connectors are also compatible with this bundle.


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
           camunda/connectors-bundle:0.18.2
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
[aws lambda connector]: ../connectors/aws-lambda
[camunda platform self-managed free edition]: https://camunda.com/legal/terms/cloud-terms-and-conditions/camunda-cloud-self-managed-free-edition-terms/
[google drive connector]: ../connectors/google-drive
[http json connector (rest)]: ../connectors/http-json
[graphql connector]: ../connectors/graphql
[rabbitmq connector]: ../connectors/rabbitmq
[kafka connector]: ../connectors/kafka
[connector runtime]: https://github.com/camunda/connector-runtime-docker
[sendgrid connector]: ../connectors/sendgrid
[slack connector]: ../connectors/slack
[ms teams connector]: ../connectors/microsoft-teams
[sns connector]: ../connectors/sns
[sqs connector]: ../connectors/sqs
[http webhook connector]: ../connectors/http-json
