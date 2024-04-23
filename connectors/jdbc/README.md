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
  "connection": {
    "authType": "URI",
    "uri": "jdbc:mysql://root:mypass@myhost1:3306/db_name",
    "properties": {
      "useSSL": false
    }
  },
  "data": {
    "isModifyingQuery": true,
    "query": "INSERT INTO Employee (Id,Name,Age,Department) VALUES (?, ?, ?, ?)",
    "variables": "[\"TheId\", \"TheName\", 42, \"TheDepartment\"]"
  }
}
```

### Input (Detailed authentication)

```json
{
  "database": "MYSQL",
  "connection": {
    "authType": "detailed",
    "host": "localhost",
    "port": 5868,
    "username": "myLogin",
    "password": "mySecretPassword",
    "properties": {
      "useSSL": false
    }
  },
  "data": {
    "isModifyingQuery": true,
    "query": "INSERT INTO Employee (Id,Name,Age,Department) VALUES (?, ?, ?, ?)",
    "variables": "[\"TheId\", \"TheName\", 42, \"TheDepartment\"]"
  }
}
```

### Output

The output depends on the value of the `isModifyingQuery` field in the input data.

If the query returns a result set (_SELECT_ query, or a query using _RETURNING_), the output will be:

```json
{
  "resultSet": [
    {
      "Id": 1,
      "Name": "John",
      "Age": 42,
      "Department": "IT"
    },
    {
      "Id": 2,
      "Name": "Jane",
      "Age": 35,
      "Department": "HR"
    }
  ]
}
```

otherwise, the output will be:

```json
{
  "modifiedRows": 1
}
```

## Element Template

The element templates can be found in
the [element-templates/jdbc-outbound-connector.json](element-templates/jdbc-outbound-connector.json) file.
