# Camunda Connectors

[![CI](https://github.com/camunda/connectors-bundle/actions/workflows/DEPLOY_SNAPSHOTS.yaml/badge.svg)](https://github.com/camunda/connectors-bundle/actions/workflows/DEPLOY_SNAPSHOTS.yaml)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.camunda.connector/connector-core/badge.svg?style=flat)](https://maven-badges.herokuapp.com/maven-central/io.camunda.connector/connector-core)
[![Outbound template](https://img.shields.io/badge/outbound_connector-use_template-blue)](https://github.com/camunda/connector-template-outbound)
[![Inbound template](https://img.shields.io/badge/inbound_connector-use_template-blue)](https://github.com/camunda/connector-template-inbound)

This is the repository for Camunda Connectors. It manages all parts of the Connectors ecosystem,
including the Connector SDK, out-of-the-box Connectors available in Camunda, the Connector Runtime, and the Docker images.

For more information on Connectors, refer to the
[Camunda documentation](https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/available-connectors-overview/).

## Contents

* [License](#license)
* [Create a Connector](#create-a-connector)
    * [Outbound Connector](#outbound-connector)
    * [Inbound Connector](#inbound-connector)
* [Connector Validation](#connector-validation)
* [Start a Connector](#start-a-connector)
* [Build](#build)
* [Build a release](#build-a-release)

## License

This is a multi-module project with different licenses applied to different modules.

### Modules available under [Apache 2.0 license](https://www.apache.org/licenses/LICENSE-2.0)

* [Connector SDK](connector-sdk) including all supporting libraries
* [Connector Secret providers](secret-providers) implementations
* [Connector Runtime](connector-runtime) and all its submodules
* [Http Json Outbound Connector](connectors/http/rest)
* [Element Template Generator](element-template-generator) and its submodules

### Modules available under [Camunda Self-Managed Free Edition license](https://camunda.com/legal/terms/cloud-terms-and-conditions/camunda-cloud-self-managed-free-edition-terms/)

* All [Out-of-the-Box Connectors](connectors) except for REST Connector (see above)
* [Docker images](bundle) of the out-of-the-box Connectors for Camunda, bundled with a runtime

When in doubt, refer to the `LICENSE` file in the respective module.

## Create a Connector

Include the [connector-core](connector-sdk/core), e.g. via Maven:

```xml
<dependency>
  <groupId>io.camunda.connector</groupId>
  <artifactId>connector-core</artifactId>
  <version>${version.connectors}</version>
  <scope>provided</scope>
</dependency>
```

Set the dependency to a `provided` scope as the runtimes that execute Connectors provide the necessary classes already.

To find the latest version, check the [Maven Central repository](https://search.maven.org/artifact/io.camunda.connector/connector-core).

### Outbound Connector

Define your Connector logic through the [`OutboundConnectorFunction`](./core/src/main/java/io/camunda/connector/api/outbound/OutboundConnectorFunction.java) interface:

```java

@OutboundConnector(
  name = "PING",
  inputVariables = {"caller"},
  type = "io.camunda.example.PingConnector:1"
)
public class PingConnector implements OutboundConnectorFunction {

  @Override
  public Object execute(OutboundConnectorContext context) throws Exception {
    var request = context.bindVariables(PingRequest.class);
    var caller = request.getCaller();
    return new PingResponse("Pong to " + caller);
  }
}
```

### Inbound Connector

Define your Connector logic through the [`InboundConnectorExecutable`](./core/src/main/java/io/camunda/connector/api/inbound/InboundConnectorExecutable.java) interface:
```java
@InboundConnector(
  name = "SUBSCRIPTION",
  type = "io.camunda.example.SubscriptionConnector:1"
)
public class SubscriptionConnector implements InboundConnectorExecutable {

  private MockSubscription subscription; // imitates some real-world subscription

  @Override
  public void activate(InboundConnectorContext context) throws Exception {
    var properties = context.bindProperties(SubscriptionProperties.class);
    // subscribe to events
    subscription = new MockSubscription(properties.getTopic());
    subscription.subscribe(event -> {
      var result = context.correlateWithResult(event);
      // handleResult(result);
    });
  }

  @Override
  public void deactivate() throws Exception {
    // unsubscribe from events
    subscription.shutdown();
  }
}
```

### Connector Discovery

The SDK provides a default implementation for Connector discovery using [Java ServiceLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html) with the [connector-runtime-core](./connector-runtime/connector-runtime-core) module.

To make your Connector discoverable, expose the `OutboundConnectorFunction` or `InboundConnectorExecutable` implementation as an SPI implementation.
Alternatively, you can use the [manual discovery mechanism](https://docs.camunda.io/docs/self-managed/connectors-deployment/connectors-configuration/#manual-discovery-of-connectors) via properties.

## Connector Validation

If you want to validate your Connector input, the SDK provides a default implementation using [Jakarta Bean Validation](https://beanvalidation.org/) with the [connector-validation](./validation) module. You can include it via maven with the following dependency:

```xml
<dependency>
  <groupId>io.camunda.connector</groupId>
  <artifactId>connector-validation</artifactId>
  <version>${version.connectors}</version>
  <scope>provided</scope>
</dependency>
```

Set the dependency to a `provided` scope as the runtimes that execute Connectors provide the necessary classes already.

Find more details in the [validation module](./validation).

## Start a Connector

[Connector runtime](connector-runtime) supports running outbound Connectors as job workers and manages the lifecycle of the inbound Connectors.
You can also build your own runtime, tailored towards your environment. For more details, refer to the [connector-runtime](connector-runtime) module.

## Build

```bash
mvn clean package
```

## Build a release

1. For minor releases (x.y.0), create a new branch `release/x.y` from `main` in advance and rebase it onto `main` from time to time. This can be done using the `CREATE_RELEASE_BRANCH` workflow.
2. To trigger the release, publish a new GitHub release and name the tag according to the version you want to release (e.g. `1.0.0`). This will trigger a GitHub workflow that builds and publishes the release artifacts, generates a changelog and bundles the element templates into an archive.

### Backport a PR to an older release

We use [backport-action](https://github.com/korthout/backport-action) to backport PRs to older releases.
For example, add a label `backport release/8.3` to backport a PR to the `release/8.3` branch. This will take effect when the PR is meged.

You can also trigger this for already merged PRs by posting a comment on the PR containing `/backport`.
