# Camunda RabbitMQ Connector

Find the user documentation in our [Camunda Platform 8 Docs](https://docs.camunda.io/docs/components/integration-framework/connectors/out-of-the-box-connectors/available-connectors-overview).

## Build

```bash
mvn clean package
```

## Outbound Connector

### API

#### Input

```json
{
  "authentication": {
    "authType": "credentials",
    "userName": "secrets.USERNAME",
    "password": "secrets.PASSWORD"
  },
  "routing": {
    "exchange": "exchangeName",
    "routingKey": "routingKey",
    "virtualHost": "virtualHostName",
    "hostName": "localhost",
    "port": "5672"
  },
  "message": {
    "body": {"messageBodyKey": "value"},
    "properties": {
      "contentType":"text/plan",
      "headers": {
        "key": "value",
        "key2":"value2"
      }
    }
  }
}
```

```json
{
  "authentication": {
    "authType": "uri",
    "uri": "amqp://userName:password@localhost:5672/virtualHostName"
  },
  "routing": {
    "exchange": "exchange",
    "routingKey": "routingKey"
  },
  "message": {
    "properties": {
      "contentType":"text/plan",
      "headers": {
        "key": "value"
      }
    },
    "body": "some data for send"
  }
}
```

#### Output

```json
{
  "result": {
    "statusResult": "success"
  }
}
```

## Inbound Connector

### API

#### Input properties

```json
{
  "authentication": {
    "authType": "credentials",
    "userName": "secrets.USERNAME",
    "password": "secrets.PASSWORD"
  },
  "routing": {
    "virtualHost": "virtualHostName",
    "hostName": "localhost",
    "port": "5672"
  },
  "queueName": "queueName",
  "consumerTag": "consumerTag",
  "arguments": {
    "key": "value"
  },
  "exclusive": "false"
}
```

```json
{
  "authentication": {
    "authType": "uri",
    "uri": "amqp://userName:password@localhost:5672/virtualHostName"
  },
  "queueName": "queueName",
  "consumerTag": "consumerTag",
  "arguments": {
    "key": "value"
  },
  "exclusive": "false"
}
```

#### Output event schema

```json
{
  "message": {
    "consumerTag": "consumerTag",
    "body": {
      "messageBodyKey": "value"
    },
    "properties": {
      "contentType": "text/plan",
      "headers": {
        "key": "value",
        "key2": "value2"
      }
    }
  }
}
```

## Element Template

The element templates can be found in the [element-templates](element-templates) directory.
