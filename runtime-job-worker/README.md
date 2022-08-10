# Job Worker Connector Run-Time

A simple run-time to execute [connector functions](../core) as job workers.

## Wrapping a Connector Function

Include the job worker run-time as maven dependency

```xml
<dependency>
  <groupId>io.camunda.connector</groupId>
  <artifactId>connector-runtime-job-worker</artifactId>
  <version>0.1.0</version>
</dependency>
```

You can create a job worker by wrapping a [connector function](../core) like this:

```java
import io.camunda.connector.slack.SlackFunction;
import io.camunda.connector.runtime.jobworker.ConnectorJobHandler;
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

We expose a simple main method to start one or more connectors directly.

You configure Zeebe using the standard [Zeebe environment variables](https://docs.camunda.io/docs/apis-clients/java-client/#bootstrapping).
Additional variables define connectors and their mapping to job workers:

| Environment variable | Purpose |
| :--- | :--- |
| `ZEEBE_CONNECTOR_{NAME}_TYPE` | Job type to register for worker with `NAME` |
| `ZEEBE_CONNECTOR_{NAME}_VARIABLES` | Variables to fetch for worker with `NAME` |
| `ZEEBE_CONNECTOR_{NAME}_FUNCTION` | Connector function to call for worker with `NAME` |

Given that configuration you can start a single job worker to execute one or more connector run-times:

```bash
ZEEBE_CONNECTOR_SLACK_TYPE=io.camunda:slack:1
ZEEBE_CONNECTOR_SLACK_VARIABLES=token,method,data
ZEEBE_CONNECTOR_SLACK_FUNCTION=io.camunda.connector.slack.SlackFunction

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
