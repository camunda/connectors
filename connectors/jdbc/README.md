# Camunda JDBC Connector

Find the user documentation in
our [Camunda Docs](https://docs.camunda.io/docs/components/integration-framework/connectors/out-of-the-box-connectors/available-connectors-overview).

## Build

```bash
mvn clean package
```

## API

### Input (Uri authentication)

```json
{
  "database": "MYSQL",
  "authentication": {
    "authType": "URI",
    "uri": "jdbc:mysql://root:mypass@myhost1:3306/db_name"
  },
  "isModifyingQuery": true,
  "query": "INSERT INTO Employee (Id,Name,Age,Department) VALUES (?, ?, ?, ?)",
  "variables": "[\"TheId\", \"TheName\", 42, \"TheDepartment\"]"
}
```

### Input (Detailed authentication)

```json
{
  "database": "MYSQL",
  "authentication": {
    "authType": "detailed",
    "host": "localhost",
    "port": 5868,
    "username": "myLogin",
    "password": "mySecretPassword"
  },
  "isModifyingQuery": true,
  "query": "INSERT INTO Employee (Id,Name,Age,Department) VALUES (?, ?, ?, ?)",
  "variables": "[\"TheId\", \"TheName\", 42, \"TheDepartment\"]"
}
```

### Output

```json
{
  "result": {
    // TODO
  }
}
```

## Element Template

// TODO to be updated
The element templates can be found in
the [element-templates/kafka--outbound-connector.json](element-templates/kafka-outbound-connector.json) file.
