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
# In docker-images.propoties
localstack=localstack/localstack:4.8
```

Then add a constant in `io.camunda.connector.test.utils.docker.DockerImages` and you can use it in your tests like this:

```
new GenericContainer<>(DockerImageName.parse(DockerImages.get(SCHEMA_REGISTRY)));
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
│   └── GenerateElementTemplate.java # Template generation
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
                    feel = Property.FeelMode.required)
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

## Build & Development Workflows

### Pull Request Guidelines

When creating pull requests:

- **Always use the PR template**: Located at `.github/PULL_REQUEST_TEMPLATE.md`
- Fill out all sections:
  - **Description**: Explain the changes and provide context
  - **Related issues**: Link to related issues (use `closes #` for issues this PR resolves)
  - **Checklist**: Verify milestone and backport labels are appropriate
- Update the PR description after creation if you initially missed the template

### Maven Structure

- **Parent POM**: `parent/pom.xml` defines versions and common configuration
- **Multi-module**: Root `pom.xml` orchestrates all modules
- **Java Versions**: SDK uses Java 17, rest of project varies
- **Profiles**: `e2eExcluded` profile skips E2E tests

### Essential Commands

```bash
# Full build
mvn clean package

# Quick build, skip long-running tests (faster development)
mvn clean package -Dquickly

# Generate element templates for connectors
./connectors/create-element-templates-symlinks.sh

# Build Docker bundle
cd bundle && docker build -t camunda/connectors-bundle:latest .
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

### Testing Patterns

- **Unit tests**: `*Test.java` classes
- **Input validation**: `*InputValidationTest.java`
- **Secrets handling**: `*SecretsTest.java`
- **Integration**: Use WireMock for HTTP mocking
- **E2E**: Separate `connectors-e2e-test/` modules

### Secret Handling

```java
// In connector implementation
var apiKey = context.getSecretStore().getSecret("MY_API_KEY");

// In tests, mock the context
when(context.getSecretStore().

getSecret("MY_API_KEY")).

thenReturn("test-key");
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

## Common Pitfalls

- **Wrong Java version**: SDK requires Java 17, ensure connector modules inherit correct version
- **Missing annotations**: Both `@OutboundConnector/@InboundConnector` AND `@ElementTemplate` required
- **Scope issues**: Use `provided` scope for SDK dependencies
- **Service registration**: Don't forget ServiceLoader registration files
- **Template generation**: Run `GenerateElementTemplate` after model changes
- **File structure changes**: When modifying file or folder structure, always check for references in CI workflows (
  `.github/workflows/`), READMEs, and this copilot instructions file

For examples, see existing connectors in `connectors/` directory. HTTP REST connector (`connectors/http/rest/`) is the
reference implementation.