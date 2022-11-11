# Job Worker Connector RunTime utilities

A collection of runtime utilities to wrap [Connector functions](../core) as job workers.

## Wrapping a Connector function

Include the job worker runtime utilities as maven dependency:

```xml

<dependency>
  <groupId>io.camunda.connector</groupId>
  <artifactId>connector-runtime-util</artifactId>
  <version>0.3.0</version>
</dependency>
```

You can create a job worker by wrapping a [Connector function](../core) like this:

```java
import io.camunda.connector.slack.SlackFunction;
import io.camunda.connector.runtime.jobworker.api.outbound.ConnectorJobHandler;
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

## Build

```bash
mvn clean package
```
