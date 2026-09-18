# Camunda Kafka Connector

Find the user documentation in
our [Camunda Docs](https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/kafka/).

## Build

```bash
mvn clean package
```

## API

### Input

```json
{
  "authentication": {
    "username": "{{secrets.KAFKA_USER_NAME}}",
    "password": "{{secrets.KAFKA_PASSWORD}}"
  },
  "topic": {
    "bootstrapServers": "{{secrets.KAFKA_BOOTSTRAP_SERVERS}}",
    "topicName": "{{secrets.KAFKA_TOPIC_NAME}}"
  },
  "message": {
    "key": "document-id-1234567890",
    "value": {
      "documentId": "1234567890",
      "signedBy": "Tester Testerson",
      "contentBase64": "Q2FtdW5kYSBLYWZrYSBDb25uZWN0b3I="
    }
  },
  "additionalProperties": {
    "delivery.timeout.ms": "25000",
    "request.timeout.ms": "25000"
  }
}
```

### Output

```json
{
  "result": {
    "topic": "topic_0",
    "timestamp": 1665927163361,
    "offset": 9,
    "partition": 1
  }
}
```

### Reusable connection credentials

The Kafka producer and consumer templates for Camunda 8.10+ offer an optional
**Connection credential** picker. A reusable **Kafka Connection** contains bootstrap
servers, username, and password, and uses `SASL_SSL` with the `PLAIN` mechanism. The
same credential can be used by both directions. The runtime must trust the brokers'
TLS certificates.

The picker binds the whole credential to `kafkaConnectionConfiguration`, using
`=camunda.vars.env.<credentialName>`. Its value has this shape:

```json
{
  "bootstrapServers": "broker1.example:9093,broker2.example:9093",
  "username": "camunda.secrets.KAFKA_USERNAME",
  "password": "camunda.secrets.KAFKA_PASSWORD"
}
```

Use the credential editor's secret references (`camunda.secrets.<name>`) for stored
credentials. The legacy `{{secrets.NAME}}` syntax shown in the inline example above
remains supported for inline inputs, but is not supported by reusable credential
validation. Credential fields are literals or secret references, not arbitrary FEEL
expressions.

The selected credential supplies both authentication and bootstrap servers. Inline
connection fields are hidden and ignored, including leftover values in an existing
model. An invalid selected credential fails validation rather than falling back to
inline fields. Topics, consumer groups, offsets, messages, headers, and schema-registry
settings remain configured on each connector element.

**Additional properties retains its existing precedence:** it can override connection
and security settings, including bootstrap servers and authentication from a credential.
A reusable credential provides storage and reuse, not a restriction on the destination.
Custom security configurations and unauthenticated connections remain available through
the inline configuration when no credential is selected.

**Test connection** performs a bounded, read-only broker metadata request. It checks
connectivity and authentication, not permission to publish to a topic, consume from a
topic, or use a consumer group. It neither publishes messages nor changes offsets.
Its result applies to the stored credential, not to per-element Additional properties
overrides. A running inbound consumer retains its existing activation/reload lifecycle;
changing a stored credential does not itself guarantee immediate reconnection.

## Element Template

The element templates can be found in
the [element-templates/kafka--outbound-connector.json](element-templates/kafka-outbound-connector.json) file.
