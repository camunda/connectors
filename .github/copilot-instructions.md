# Camunda Connectors Development Guide

This repository contains the Camunda Connectors ecosystem - a comprehensive framework for building and managing
connectors for Camunda Platform 8.

## Project Architecture

### Core Components

- **`connector-sdk/`**: SDK for building connectors (Java 17)
    - `core/`: Core interfaces (`OutboundConnectorFunction`, `InboundConnectorExecutable`)
    - `validation/`: Jakarta Bean Validation support
    - `test/`: Testing utilities for connector development
- **`connector-runtime/`**: Execution environment for connectors
    - `connector-runtime-core/`: Framework-independent runtime
    - `connector-runtime-spring/`: Spring-based runtime
    - `spring-boot-starter-camunda-connectors/`: Spring Boot starter (recommended)
- **`connectors/`**: 30+ out-of-the-box connectors (AWS, HTTP, Kafka, etc.)
- **`element-template-generator/`**: Automated generation of Camunda Modeler templates
- **`bundle/`**: Docker images bundling runtime + connectors
- **`connector-commons/`**: Shared utilities
    - `connector-object-mapper/`: Jackson ObjectMapper configuration
    - `connector-test-utils/`: Testing utilities including `@SlowTest`, `DockerImages`
    - `http-client/`: Shared HTTP client implementation

### Multi-License Structure

- **Apache 2.0**: SDK, Runtime, HTTP REST Connector, Element Template Generator
- **Camunda Self-Managed Free Edition**: Most out-of-the-box connectors

## Connector Development Patterns

### Using Docker images in tests

When writing integration or end-to-end tests that require a service (like a database, message broker, etc.), it's
recommended to use Docker images to ensure consistency and isolation. You can use libraries like Testcontainers to
manage the lifecycle of these containers during your tests.

When doing so, make sure to use `io.camunda.connector.test.utils.DockerImages`.
You need to add your image in a docker-images.properties file (in the test `resources` folder), for
example:

```
# In docker-images.properties
localstack=localstack/localstack:4.8
kafka=confluentinc/cp-kafka:7.4.0
```

Then add a constant in `io.camunda.connector.test.utils.docker.DockerImages` and you can use it in your tests like this:

```java
new GenericContainer<>(DockerImageName.

parse(DockerImages.get(SCHEMA_REGISTRY)));
```

### Standard Connector Structure

```
my-connector/
├── pom.xml                           # Maven module
├── src/main/java/
│   └── io/camunda/connector/
│       ├── MyConnectorFunction.java  # Main implementation
│       └── model/
│           ├── MyRequest.java        # Input model
│           └── MyResponse.java       # Output model
├── src/test/java/
│   ├── MyConnectorTest.java         # Unit tests
│   ├── BaseTest.java                # Shared test utilities
│   └── GenerateElementTemplate.java # Template generation
├── src/test/resources/
│   └── docker-images.properties     # Docker images for integration tests
└── element-templates/               # Generated templates
    └── my-connector.json
```

### Outbound Connector Implementation

```java

@OutboundConnector(
        name = "MY_CONNECTOR",
        inputVariables = {"field1", "field2"},
        type = "io.camunda.connector:my-connector:1")
@ElementTemplate(
        id = "io.camunda.connectors.MyConnector.v1",
        name = "My Connector",
        inputDataClass = MyRequest.class,
        outputDataClass = MyResponse.class,
        propertyGroups = {
                @PropertyGroup(id = "auth", label = "Authentication"),
                @PropertyGroup(id = "config", label = "Configuration")
        })
public class MyConnectorFunction implements OutboundConnectorFunction {

    @Override
    public Object execute(OutboundConnectorContext context) throws Exception {
        var request = context.bindVariables(MyRequest.class);
        // Implementation logic
        return new MyResponse(result);
    }
}
```

### Operation-Based Outbound Connector Implementation (New Preferred Syntax)

For connectors that involve multiple operations, the new operation-based syntax is preferred. This approach uses
`@Operation` annotations to define multiple operations within a single connector class:

```java

@OutboundConnector(name = "CSV Connector", type = "io.camunda:csv-connector")
@ElementTemplate(
        name = "CSV Connector",
        id = "io.camunda.connectors.csv",
        version = 1,
        engineVersion = "^8.8",
        icon = "icon.svg")
public class CsvConnector implements OutboundConnectorProvider {

    @Operation(id = "readCsv", name = "Read CSV")
    public ReadCsvResult readCsv(
            @Variable ReadCsvRequest request,
            @Header(name = "recordMapper", required = false)
            @TemplateProperty(
                    label = "Record mapping",
                    tooltip = "FEEL function that allows to map each record",
                    feel = TemplateProperty.FeelMode.required)
            Function<Map<String, Object>, Object> mapper) {
        // Read CSV implementation
        return readCsvRequest(/* ... */);
    }

    @Operation(id = "writeCsv", name = "Write CSV")
    public Object writeCsv(@Variable WriteCsvRequest request, OutboundConnectorContext context) {
        // Write CSV implementation
        var csv = createCsv(request.data(), request.format());
        return new WriteCsvResult(csv);
    }
}
```

**Key differences from traditional syntax:**

- Implements `OutboundConnectorProvider` instead of `OutboundConnectorFunction`
- Uses `@Operation` annotations to define multiple operations
- Each operation method can have different input/output types
- Uses `@Variable` and `@Header` annotations for parameter binding
- Better suited for connectors with multiple distinct operations

**Note:** Both syntaxes are still supported. Use the operation-based syntax for connectors with multiple operations, and
the traditional syntax for simple single-operation connectors.

### Inbound Connector Implementation

```java

@InboundConnector(
        name = "MY_INBOUND",
        type = "io.camunda.connector:my-inbound:1")
public class MyInboundConnector implements InboundConnectorExecutable {

    @Override
    public void activate(InboundConnectorContext context) throws Exception {
        var props = context.bindProperties(MyProperties.class);
        // Setup subscription/polling logic
        // Use context.correlateWithResult(data) to trigger processes
    }

    @Override
    public void deactivate() throws Exception {
        // Cleanup resources
    }
}
```

## Input Model Patterns

### Authentication Pattern

Use sealed interfaces with `@JsonTypeInfo` for authentication types:

```java

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = BasicAuthentication.class, name = BasicAuthentication.TYPE),
        @JsonSubTypes.Type(value = NoAuthentication.class, name = NoAuthentication.TYPE),
        @JsonSubTypes.Type(value = OAuthAuthentication.class, name = OAuthAuthentication.TYPE),
        @JsonSubTypes.Type(value = BearerAuthentication.class, name = BearerAuthentication.TYPE),
        @JsonSubTypes.Type(value = ApiKeyAuthentication.class, name = ApiKeyAuthentication.TYPE)
})
@TemplateDiscriminatorProperty(
        label = "Type",
        group = "authentication",
        name = "type",
        defaultValue = NoAuthentication.TYPE,
        description = "Choose the authentication type")
public sealed interface Authentication
        permits ApiKeyAuthentication, BasicAuthentication, BearerAuthentication,
        NoAuthentication, OAuthAuthentication {
}
```

### Property Validation

Use Jakarta Bean Validation annotations that are automatically converted to element template constraints:

```java
public record MyRequest(
        @NotEmpty
        @TemplateProperty(group = "config", label = "URL")
        String url,

        @NotBlank  // Results in notEmpty constraint
        @TemplateProperty(group = "config", label = "Name")
        String name,

        @Size(min = 1, max = 100)  // Results in minLength/maxLength constraints
        @TemplateProperty(group = "config", label = "Description")
        String description,

        @Pattern(regexp = "^https?://.*")
        @TemplateProperty(group = "config", label = "Endpoint")
        String endpoint
) {
}
```

Supported annotations: `@NotEmpty`, `@NotBlank`, `@NotNull`, `@Size`, `@Pattern`

### FEEL Expression Support

Use `@FEEL` annotation for properties that should support FEEL expressions:

```java
public record MyRequest(
        @FEEL
        @NotEmpty
        @TemplateProperty(group = "config", label = "Dynamic Value")
        String dynamicValue
) {
}
```

### Document Handling

For file upload/download operations, use the `Document` type and `DocumentCreationRequest`:

```java
// Uploading a document
public record UploadRequest(
                @NotNull
                @TemplateProperty(
                        label = "Document",
                        type = TemplateProperty.PropertyType.String,
                        feel = TemplateProperty.FeelMode.required)
                Document document
        ) {
}

// Creating a document from bytes
Document document = context.create(
        DocumentCreationRequest.from(content)
                .fileName("file.txt")
                .contentType("text/plain")
                .build());
```

## Build & Development Workflows

### Maven Structure

- **Parent POM**: `parent/pom.xml` defines versions and common configuration
- **Multi-module**: Root `pom.xml` orchestrates all modules
- **Java Versions**:
    - SDK uses Java 17 (`version.java.connector-sdk`)
    - Rest of project uses Java 21 (`version.java`)
- **Profiles**: `e2eExcluded` profile skips E2E tests

### Essential Commands

```bash
# Full build
mvn clean package

# Quick build, skip long-running tests (faster development)
mvn clean package -Dquickly

# Quick build with Maven cache (fastest for incremental builds)
mvn clean install -DskipTests -DskipChecks -Dquickly -Dmaven.build.cache.enabled=true -T 1C

# Generate element templates for connectors
./connectors/create-element-templates-symlinks.sh

# Build Docker bundle
cd bundle && docker build -t camunda/connectors-bundle:latest .

# Run specific test class
mvn test -pl connectors/http/rest -Dtest=HttpJsonFunctionTest

# Run integration tests (requires Docker)
mvn verify -pl connectors/kafka
```

### Element Template Generation

Every connector should include a `GenerateElementTemplate.java` test class:

```java
public class GenerateElementTemplate {
    public static void main(String[] args) throws JsonProcessingException {
        System.out.println(new ObjectMapper().writeValueAsString(
                new ClassBasedTemplateGenerator().generate(MyConnectorFunction.class)));
    }
}
```

## Development Conventions

### Package Structure

- Connectors: `io.camunda.connector.{service}`
- Models: `io.camunda.connector.{service}.model`
- Tests follow same package structure in `src/test/java`

### Dependency Management

- Set connector SDK dependencies to `<scope>provided</scope>`
- Runtime provides core classes, connectors only add implementation
- Use `connector-http-base` for HTTP-related connectors

### ObjectMapper Usage

Always use `ConnectorsObjectMapperSupplier.getCopy()` for JSON serialization:

```java
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;

// Get a pre-configured ObjectMapper instance
ObjectMapper objectMapper = ConnectorsObjectMapperSupplier.getCopy();
```

The default configuration includes:

- Java 8 date/time support (`JavaTimeModule`)
- Case-insensitive enum handling
- Single value to array conversion
- Unknown properties are ignored

### Error Handling

Use `ConnectorException` and `ConnectorRetryException` for proper error handling:

```java
// Simple error
throw new ConnectorException("ERROR_CODE","Error message");

// Retryable error with custom retry configuration
throw ConnectorRetryException.

builder()
    .

errorCode("TRANSIENT_ERROR")
    .

message("Temporary failure")
    .

retries(3)
    .

backoffDuration(Duration.ofSeconds(10))
        .

build();

// BPMN error (caught by error boundary events)
throw new

BpmnError("VALIDATION_ERROR","Input validation failed");
```

### Testing Patterns

- **Unit tests**: `*Test.java` classes
- **Input validation**: `*InputValidationTest.java`
- **Secrets handling**: `*SecretsTest.java`
- **Integration**: Use WireMock for HTTP mocking
- **E2E**: Separate `connectors-e2e-test/` modules
- **Slow tests**: Add `@SlowTest` annotation to integration tests

#### BaseTest Pattern

Create a `BaseTest` class for shared test utilities:

```java
public abstract class BaseTest {
    protected interface ActualValue {
        String TOKEN = "test-token";
        // Other actual values
    }

    protected interface SecretsConstant {
        String TOKEN = "TOKEN_KEY";
        // Other secret keys
    }

    protected static OutboundConnectorContextBuilder getContextBuilderWithSecrets() {
        return OutboundConnectorContextBuilder.create()
                .validation(new DefaultValidationProvider())
                .secret(SecretsConstant.TOKEN, ActualValue.TOKEN);
    }

    protected static Stream<String> loadTestCasesFromResourceFile(String path) throws IOException {
        // Load JSON test cases from resources
    }
}
```

#### WireMock Integration

```java

@WireMockTest
public class MyConnectorIntegrationTest {
    @BeforeAll
    public static void setup(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(get(urlEqualTo("/api/resource"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"result\": \"success\"}")));
    }
}
```

#### Parameterized Tests with JSON Files

```java

@ParameterizedTest
@MethodSource("successTestCases")
void shouldExecuteSuccessfully(String input) throws Exception {
    var context = getContextBuilderWithSecrets().variables(input).build();
    var result = connector.execute(context);
    assertThat(result).isNotNull();
}

private static Stream<String> successTestCases() throws IOException {
    return loadTestCasesFromResourceFile("src/test/resources/success-cases.json");
}
```

### Secret Handling

```java
// In connector implementation
var apiKey = context.getSecretStore().getSecret("MY_API_KEY");

// In tests, mock the context
when(context.getSecretStore().

getSecret("MY_API_KEY")).

thenReturn("test-key");
```

## Runtime Configuration

### Spring Boot Properties

```properties
# Camunda SaaS connection
camunda.client.cloud.cluster-id=xxx
camunda.client.auth.client-id=xxx
camunda.client.auth.client-secret=xxx
camunda.client.cloud.region=bru-2
# Local Zeebe connection
camunda.client.grpc-address=http://127.0.0.1:26500
# Connector runtime settings
camunda.connector.polling.enabled=true
camunda.connector.polling.interval=5000
camunda.connector.webhook.enabled=true
# Secret provider configuration
camunda.connector.secretProvider.environment.enabled=true
camunda.connector.secretProvider.environment.prefix=
```

### Environment Variable Overrides

Connector configuration can be overridden via environment variables:

- `CONNECTOR_{NAME}_FUNCTION`: Function class to register
- `CONNECTOR_{NAME}_TYPE`: Job type override
- `CONNECTOR_{NAME}_INPUT_VARIABLES`: Variables to fetch

Example:

```bash
CONNECTOR_HTTPJSON_FUNCTION=io.camunda.connector.http.rest.HttpJsonFunction
CONNECTOR_HTTPJSON_TYPE=custom-http-type
```

## Key Integration Points

### Service Discovery

Connectors are discovered via Java ServiceLoader. Create `META-INF/services/` files:

- `io.camunda.connector.api.outbound.OutboundConnectorFunction`
- `io.camunda.connector.api.inbound.InboundConnectorExecutable`

### Runtime Integration

- **Standalone**: Use `connector-runtime-application`
- **Spring Boot**: Add `spring-boot-starter-camunda-connectors` dependency
- **Docker**: Extend `camunda/connectors-bundle` image

### Element Template Deployment

Templates auto-link to Camunda Modeler via `create-element-templates-symlinks.sh` script targeting
`~/Library/Application Support/camunda-modeler/resources/element-templates/` (macOS).

## CI/CD Workflows

Key workflow files in `.github/workflows/`:

- **`DEPLOY_SNAPSHOTS.yaml`**: Deploys snapshot artifacts on push to `main`, `stable/**`, `alpha/**`
- **`RELEASE.yaml`**: Handles releases
- **`INTEGRATION_TEST.yml`**: Runs integration tests
- **`NIGHTLY_E2E.yml`**: Nightly E2E test runs
- **`CHECK_LICENSES.yml`**: License compliance checks

### Branch Naming

- `main`: Main development branch
- `stable/**`: Stable release branches
- `alpha/**`: Alpha release branches

## Common Pitfalls

- **Wrong Java version**: SDK requires Java 17, ensure connector modules inherit correct version
- **Missing annotations**: Both `@OutboundConnector/@InboundConnector` AND `@ElementTemplate` required
- **Scope issues**: Use `provided` scope for SDK dependencies
- **Service registration**: Don't forget ServiceLoader registration files
- **Template generation**: Run `GenerateElementTemplate` after model changes
- **File structure changes**: When modifying file or folder structure, always check for references in CI workflows (
  `.github/workflows/`), READMEs, and this copilot instructions file
- **ObjectMapper**: Always use `ConnectorsObjectMapperSupplier.getCopy()` instead of creating new instances
- **Docker images**: Use `DockerImages.get()` utility for Testcontainers, add images to `docker-images.properties`
- **Integration tests**: Mark with `@SlowTest` to exclude from regular unit test runs
- **FEEL expressions**: Use `@FEEL` annotation for properties that need runtime evaluation

## Reference Implementations

For examples, see existing connectors in `connectors/` directory:

- **HTTP REST connector** (`connectors/http/rest/`): Reference implementation for outbound connectors
- **Webhook connector** (`connectors/webhook/`): Reference implementation for inbound connectors
- **Kafka connector** (`connectors/kafka/`): Example of both inbound and outbound patterns
- **Azure Blob Storage** (`connectors/microsoft/azure-blobstorage/`): Document handling example
