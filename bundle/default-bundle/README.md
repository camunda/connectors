# Running Connector Runtime Locally

## Running Options

### Option 1: Maven / IDE

1. Launch via IDE by running the `io.camunda.connector.runtime.app.LocalConnectorRuntime` class
2. Configure by adding `application.properties` to `src/test/resources`

### Option 2: Command Line

```bash
# java \
  -Dzeebe.client.cloud.region=bru-2 \
  -Dzeebe.client.cloud.clusterId=xxx \
  -Dzeebe.client.cloud.clientId=yyy \
  -Dzeebe.client.cloud.clientSecret=zzz \
  -jar target/connector-runtime-bundle-{VERSION}-with-dependencies.jar
```

## Update: Migration to `camunda-distributions` repository

### Why Migrate?

- The `camunda-platform` is now deprecated

### Migration Steps

1. Clone [the repository](https://github.com/camunda/camunda-distributions/tree/main)
2. Comment out the `connectors` section in the configuration
3. Update your endpoints:
    - **Inbound Connectors Webhook**: `http://localhost:8085/inbound/` (Note the port change)
    - **Operate Interface**: `http://localhost:8081/operate/login` (Now included in core, note the port change)

### Key Changes

- Operate is now integrated into the core distribution
- Updated port configurations for both webhook and Operate interface
- Improved compatibility with current connector implementations