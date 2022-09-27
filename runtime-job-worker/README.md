# Job Worker Connector Run-Time

A simple run-time to execute [connector functions](../core) as job workers.

## Wrapping a Connector Function

Include the job worker run-time as maven dependency

```xml

<dependency>
  <groupId>io.camunda.connector</groupId>
  <artifactId>connector-runtime-job-worker</artifactId>
  <version>0.2.0</version>
</dependency>
```

You can create a job worker by wrapping a [connector function](../core) like this:

```java
import io.camunda.connector.slack.SlackFunction;
import io.camunda.connector.runtime.jobworker.outbound.ConnectorJobHandler;
import io.camunda.zeebe.client.ZeebeClient;

public class Main {

  public static void main(String[] args) {

    var zeebeClient = ZeebeClient.newClientBuilder().build();

    zeebeClient.newWorker()
        .jobType("slack")
        .handler(new ConnectorJobHandler(new SlackFunction()))
        .name("SLACK")
        .fetchVariables("foo", "bar")
        .open();
  }
}
```

## Running Connector Function(s)

We expose a main method to run one or more connectors and register them
as [Zeebe job workers](https://docs.camunda.io/docs/next/components/concepts/job-workers/).

### Connecting to Zeebe

You configure the connection to Zeebe using the
standard [Zeebe environment variables](https://docs.camunda.io/docs/apis-clients/java-client/#bootstrapping).

### Automatic Connector Discovery

The run-time picks up outbound connectors available on the classpath automatically unless [overriden through manual configuration](#manual-discovery).
It uses the default configuration specified through the `@OutboundConnector` annotation in these cases.

```bash
java -cp 'connector-runtime-job-worker.jar;cloud-connector-slack.jar' \
    io.camunda.connector.runtime.jobworker.Main
```

### Manual Discovery

Use environment variables to configure connectors and their configuration explicitly, without [auto-discovery](#automatic-connector-discovery):

| Environment variable                          | Purpose                                                       |
|:----------------------------------------------|:--------------------------------------------------------------|
| `CONNECTOR_{NAME}_FUNCTION` (required)        | Function to be registered as job worker with the given `NAME` |
| `CONNECTOR_{NAME}_TYPE` (optional)            | Job type to register for worker with `NAME`                   |
| `CONNECTOR_{NAME}_INPUT_VARIABLES` (optional) | Variables to fetch for worker with `NAME`                     |

Through that configuration you define all job workers to run.
Specifying optional values allow you to override `@OutboundConnector` provided connector configuration.

```bash
CONNECTOR_SLACK_FUNCTION=io.camunda.connector.slack.SlackFunction
CONNECTOR_SLACK_TYPE=non-default-slack-task-type

java -cp 'connector-runtime-job-worker.jar;cloud-connector-slack.jar' \
    io.camunda.connector.runtime.jobworker.Main
```

## Local secrets

To inject secrets during connector function execution, export them as environment variables

```bash
export MY_SECRET='foo'
```

Reference the secret in the request payload prefixed with `secrets.MY_SECRET`.

## Build

```bash
mvn clean package
```
