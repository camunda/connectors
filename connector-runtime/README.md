# Camunda Connector Runtime

Modules in this directory contain the standard implementation of a **Connector runtime** for Camunda 8 Connectors.

## Runtime modules

Camunda 8 Connector runtime is shipped as 3 artifacts:

* [`connector-runtime-core`](connector-runtime-core): The core runtime classes and utilities, independent of any framework.
This module only provides a basic library that can be used to build a custom Connector runtime.
* [`connector-runtime-spring`](connector-runtime-spring): The Spring-based runtime implementation.
Built on top of `connector-runtime-core`, this module provides a Spring configuration for an embedded Connector Runtime.
* [`spring-boot-starter-camunda-connectors`](spring-boot-starter-camunda-connectors): A Spring Boot starter for the Connector Runtime.
This module provides a Spring Boot autoconfiguration for an embedded Connector Runtime. It is built on top of `connector-runtime-spring`.
**The Spring Boot starter is the recommended way of using the Connector Runtime in a Spring Boot application.**

## Usage

### Run in your Spring Boot project

To add the embeddable Connector runtime to your Spring Boot project, add the following dependency to your Spring project:

```xml
<dependency>
  <groupId>io.camunda.connector</groupId>
  <artifactId>spring-boot-starter-camunda-connectors</artifactId>
  <version>${version.connectors}</version>
</dependency>
```

Refer to the [Releases](https://github.com/camunda/connectors-bundle/releases) page for the latest version.

### Via Docker

Refer to the [Connector Runtime Docker image documentation](https://github.com/camunda/connector-runtime-docker) for further details.

To use the Camunda-provided Connectors with the runtime out of the box, refer to the [Connectors Bundle](../bundle).

## Building Connector runtime bundles

Often, you want to build one application/deployment, that not only contains the pure runtime, but also some connectors themselves.

There are two ways of achieving this:

1. Create a Maven build that depends on this runtime, but also add connectors as dependencies.
2. Add connectors to the classpath of the runtime.

The **first approach (Maven)** has the clear advantage, that Maven resolves possible dependency conflicts,
for example because two connectors use different versions of Jackson.

For example:

```xml
  <dependencies>
    <dependency>
      <groupId>io.camunda</groupId>
      <artifactId>spring-boot-starter-camunda-connectors</artifactId>
      <version>${version.spring-zeebe}</version>
    </dependency>
    <dependency>
      <groupId>io.camunda.connector</groupId>
      <artifactId>connector-http-json</artifactId>
      <version>${version.connector-http-json}</version>
    </dependency>
    <dependency>
      <groupId>io.camunda.connector</groupId>
      <artifactId>connector-sqs</artifactId>
      <version>${version.connector-sqs}</version>
    </dependency>
  </dependencies>
```

We generally recommend preferring this approach.


The **second approach (Classpath)** has the advantage, that you con build a runtime by pure
command line or Docker instructions, without any Java knowledge.


To minimize the risk of connectors bringing different versions of the same library,
we recommend to use [Maven Shade Relocate](https://maven.apache.org/plugins/maven-shade-plugin/examples/class-relocation.html)
to relocate common libraries. A [example can be found in the SQS connector](https://github.com/camunda/connector-sqs/blob/main/pom.xml#L111):

```xml
  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-shade-plugin</artifactId>
        <configuration>
          <relocations>
            <relocation>
              <pattern>com.fasterxml.jackson</pattern>
              <shadedPattern>connectorsqs.com.fasterxml.jackson</shadedPattern>
            </relocation>
          </relocations>
        </configuration>
      </plugin>
    </plugins>
  </build>
```

Now SQS bundles its own copy of Jackson.

See also [Docker: Custom set of connectors](https://docs.camunda.io/docs/self-managed/platform-deployment/docker/#custom-set-of-connectors).

## Configuration Options

The following configuration properties can be set via `src/main/application.properties` if you run Java directly.

You can also set those configuration options via environment variables (then named `ZEEBE_CLIENT_CLOUD_CLUSTER-ID` instead of `zeebe.client.cloud.cluster-id`), especially useful if you run via DOCKER.

In general, the Connector Runtime will respect all properties known to [Spring Zeebe](https://github.com/camunda-community-hub/spring-zeebe), which allows to specify some more options.

### Configure Camunda Platform

#### SaaS

To use Camunda Platform 8 SaaS specify the connection properties:

```properties
zeebe.client.cloud.cluster-id=xxx
zeebe.client.cloud.client-id=xxx
zeebe.client.cloud.client-secret=xxx
zeebe.client.cloud.region=bru-2
```

You can further configure separate connection properties for Camunda Operate (othewise it will use the properties configured for Zeebe above):

```properties
camunda.operate.client.client-id=xxx
camunda.operate.client.client-secret=xxx
```

#### Local installation

Zeebe:

```properties
zeebe.client.broker.gateway-address=127.0.0.1:26500
zeebe.client.security.plaintext=true
```

Connect to Operate locally using username and password:

```properties
camunda.operate.client.url=http://localhost:8081
camunda.operate.client.username=demo
camunda.operate.client.password=demo
```

When running against a self-managed environment you might also need to configure the keycloak endpoint to not use Operate username/password authentication:

```properties
camunda.operate.client.keycloak-url=http://localhost:18080
camunda.operate.client.keycloak-realm=camunda-platform
camunda.operate.client.client-id=xxx
camunda.operate.client.client-secret=xxx
```

### Adding Connectors

#### Automatic Connector Discovery

The runtime picks up outbound connectors available on the classpath automatically unless [overriden through manual configuration](#manual-discovery).
The automatic discovery is based on the Java ServiceLoader mechanism.
It looks for all classes implementing the `io.camunda.connector.api.outbound.OutboundConnectorFunction` 
or `io.camunda.connector.api.inbound.InboundConnectorExecutable` interfaces. For automatic discovery to work,
you need to create a file `META-INF/services/io.camunda.connector.api.outbound.OutboundConnectorFunction`
or `META-INF/services/io.camunda.connector.api.inbound.InboundConnectorExecutable` in your connector jar file.
This file must contain the fully qualified class name of your connector class.

It uses the default configuration specified through the `@OutboundConnector` annotation in these cases.

```bash
java -cp 'connector-runtime-VERSION-with-dependencies.jar:connector-http-json-VERSION-with-dependencies.jar' \
    io.camunda.connector.bundle.ConnectorRuntimeApplication
```

Note that you need to use `;` instead of `:` on Windows machines:

```bash
java -cp 'connector-runtime-VERSION-with-dependencies.jar;connector-http-json-VERSION-with-dependencies.jar' \
    io.camunda.connector.bundle.ConnectorRuntimeApplication
```

#### Manual Discovery

Use environment variables to configure connectors and their configuration explicitly, without [auto-discovery](#automatic-connector-discovery):

| Environment variable                          | Purpose                                                       |
|:----------------------------------------------|:--------------------------------------------------------------|
| `CONNECTOR_{NAME}_FUNCTION` (required)        | Function to be registered as job worker with the given `NAME` |
| `CONNECTOR_{NAME}_TYPE` (optional)            | Job type to register for worker with `NAME`                   |
| `CONNECTOR_{NAME}_INPUT_VARIABLES` (optional) | Variables to fetch for worker with `NAME`                     |

Through that configuration you define all job workers to run.
Specifying optional values allow you to override `@OutboundConnector` provided connector configuration.

```bash
CONNECTOR_HTTPJSON_FUNCTION=io.camunda.connector.http.HttpJsonFunction
CONNECTOR_HTTPJSON_TYPE=non-default-httpjson-task-type

java -cp 'connector-runtime-VERSION-with-dependencies.jar:connector-http-json-VERSION-with-dependencies.jar' \
    io.camunda.connector.bundle.ConnectorRuntimeApplication
```

## Secrets

You can define a custom secret provider by implementing the [`SecretProvider`](https://github.com/camunda/connector-sdk/blob/main/core/src/main/java/io/camunda/connector/api/secret/SecretProvider.java) interface.
This secret provider is used by the Runtime to resolve secrets. You can also define multiple secret providers.

### Secret Provider Discoverability

You can make your custom secret provider discoverable in 2 ways:
- Using the Java `SeviceLoader` pattern
    - Add a file `META-INF/services/io.camunda.connector.api.secret.SecretProvider` to the classpath
    - The file should contain the fully qualified name of your secret provider class
    - This method is implemented in the [Connector SDK](https://github.com/camunda/connector-sdk) and is also supported in custom Connector Runtimes out of the box
- By defining your `SecretProvider` as a Spring bean
    - All Spring beans implementing the `SecretProvider` interface are automatically discovered by the Runtime
    - Note that if you define a custom secret provider as a Spring bean, other providers defined using the `ServiceLoader` pattern will not be discovered

When using `spring-boot-starter-camunda-connectors`, the secret providers are discovered in the following order:
1. Secret providers defined as Spring beans have the highest priority. If they are discovered, the `ServiceLoader` pattern is not used.
2. Secret providers defined using the `ServiceLoader` pattern have the second priority.
3. If no secret provider is discovered, the default secret provider is used. It uses the environment variables to resolve secrets.

Therefore, a combination of 2 ways of defining secret providers is **not possible**.
If you want to define multiple secret providers, make sure that all of them are defined using the same method (e.g. all of them are Spring beans).

### Secret Provider priority

If multiple secret providers are discovered, all of them can be used to resolve secrets.
The default strategy is to use the first secret provider that returns a non-null value for a given secret name. The order of lookup is non-deterministic.

You can change this behavior by implementing the `SecretProviderAggregator` interface. Define your custom aggregator as a Spring bean, and it will be used instead of the default one.

### Secrets as environment variables

To inject secrets during connector function execution, export them as environment variables

```bash
export MY_SECRET='foo'
```

Reference the secret in the request payload prefixed with `secrets.MY_SECRET`.

### Docker Image Secrets

Refer to the [Connector Runtime Docker image documentation](https://github.com/camunda/connector-runtime-docker/blob/main/README.md#secrets) for further details.


## License

Use under terms of the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)
