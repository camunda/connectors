/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.outbound.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.kafka.model.FixedSubjectNameStrategy;
import io.camunda.connector.kafka.model.KafkaAuthentication;
import io.camunda.connector.kafka.model.KafkaPropertiesUtil;
import io.camunda.connector.kafka.model.KafkaTopic;
import io.camunda.connector.kafka.model.SchemaSource;
import io.camunda.connector.kafka.model.SchemaType;
import io.camunda.connector.kafka.model.schema.OutboundSchemaRegistryStrategy;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.avro.AvroSchemaProvider;
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference;
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaSerializer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.common.errors.SerializationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RegistrySchemaResolverTest {

  private static final String V1 =
      "{\"type\":\"record\",\"name\":\"Task\",\"fields\":[{\"name\":\"id\",\"type\":\"string\"}]}";
  private static final String V2 =
      "{\"type\":\"record\",\"name\":\"Task\",\"fields\":[{\"name\":\"id\",\"type\":\"string\"},"
          + "{\"name\":\"note\",\"type\":[\"null\",\"string\"],\"default\":null}]}";
  private static final String ADDRESS =
      "{\"type\":\"record\",\"name\":\"Address\",\"fields\":[{\"name\":\"street\",\"type\":\"string\"},"
          + "{\"name\":\"city\",\"type\":\"string\"}]}";

  private final MockSchemaRegistryClient client =
      new MockSchemaRegistryClient(List.of(new AvroSchemaProvider(), new JsonSchemaProvider()));
  private final RegistrySchemaResolver resolver =
      new RegistrySchemaResolver((url, configs) -> client);
  private int idV1;
  private int idV2;

  @BeforeEach
  void register() throws Exception {
    idV1 = client.register("orders-value", new AvroSchema(V1));
    idV2 = client.register("orders-value", new AvroSchema(V2));
  }

  @Test
  void inlineSourceIsNotResolved() throws Exception {
    var request = request(strategy(SchemaSource.INLINE, null, null, null));
    assertThat(resolver.resolve(request)).isEmpty();
  }

  @Test
  void missingSourceBehavesLikeInline() throws Exception {
    var request = request(strategy(null, null, null, null));
    assertThat(resolver.resolve(request)).isEmpty();
  }

  @Test
  void resolvesLatestVersionOfDefaultSubject() throws Exception {
    var resolved = resolver.resolve(request(strategy(SchemaSource.REGISTRY, null, null, null)));

    assertThat(resolved).get().extracting(ResolvedSchema::id).isEqualTo(idV2);
    assertThat(resolved.get().subject()).isEqualTo("orders-value");
  }

  @Test
  void resolvesPinnedVersion() throws Exception {
    var resolved = resolver.resolve(request(strategy(SchemaSource.REGISTRY, null, 1, null)));

    assertThat(resolved).get().extracting(ResolvedSchema::id).isEqualTo(idV1);
  }

  @Test
  void schemaIdTakesPrecedenceOverVersion() throws Exception {
    var resolved = resolver.resolve(request(strategy(SchemaSource.REGISTRY, null, 2, idV1)));

    assertThat(resolved).get().extracting(ResolvedSchema::id).isEqualTo(idV1);
  }

  @Test
  void usesExplicitSubject() throws Exception {
    client.register("other", new AvroSchema(V1));
    var resolved = resolver.resolve(request(strategy(SchemaSource.REGISTRY, "other", null, null)));

    assertThat(resolved).get().extracting(ResolvedSchema::subject).isEqualTo("other");
  }

  @Test
  void failsForUnknownSubject() {
    var request = request(strategy(SchemaSource.REGISTRY, "missing", null, null));

    assertThatThrownBy(() -> resolver.resolve(request)).isInstanceOf(Exception.class);
  }

  @Test
  void buildsRecordFromRegisteredSchemaWithoutInlineSchema() throws Exception {
    var request = request(strategy(SchemaSource.REGISTRY, null, null, null));
    var resolved = resolver.resolve(request);

    var record = new ProducerRecordFactory().createProducerRecord(request, resolved);

    assertThat(record.value()).isInstanceOf(GenericRecord.class);
    assertThat(((GenericRecord) record.value()).getSchema().getField("note")).isNotNull();
  }

  @Test
  void serializerIsPinnedToResolvedSchemaAndNeverRegisters() throws Exception {
    var request = request(strategy(SchemaSource.REGISTRY, "orders-value", 1, null));
    var resolved = resolver.resolve(request);

    var props = KafkaPropertiesUtil.assembleKafkaClientProperties(request, resolved);

    assertThat(props)
        .containsEntry("auto.register.schemas", false)
        .containsEntry("use.latest.version", false)
        .containsEntry("use.schema.id", idV1)
        .containsEntry("camunda.connector.fixed.subject", "orders-value");
  }

  @Test
  void additionalPropertiesCannotOverrideTheRegistryModeSettings() throws Exception {
    var request =
        new KafkaConnectorRequest(
            null,
            new KafkaTopic("localhost:9092", "orders"),
            new KafkaMessage("key", Map.of("id", "42")),
            strategy(SchemaSource.REGISTRY, "orders-value", 1, null),
            null,
            Map.of(
                "use.schema.id",
                999,
                "auto.register.schemas",
                true,
                "use.latest.version",
                true,
                "value.subject.name.strategy",
                "io.confluent.kafka.serializers.subject.TopicNameStrategy",
                "camunda.connector.fixed.subject",
                "other-value"));
    var resolved = resolver.resolve(request);

    var props = KafkaPropertiesUtil.assembleKafkaClientProperties(request, resolved);

    assertThat(props)
        .containsEntry("use.schema.id", idV1)
        .containsEntry("auto.register.schemas", false)
        .containsEntry("use.latest.version", false)
        .containsEntry("value.subject.name.strategy", FixedSubjectNameStrategy.class.getName())
        .containsEntry("camunda.connector.fixed.subject", "orders-value");
  }

  @Test
  void additionalPropertiesStillApplyWhenTheSchemaIsInline() {
    var request =
        new KafkaConnectorRequest(
            null,
            new KafkaTopic("localhost:9092", "orders"),
            new KafkaMessage("key", Map.of("id", "42")),
            strategy(SchemaSource.INLINE, null, null, null),
            null,
            Map.of("auto.register.schemas", false));

    var props = KafkaPropertiesUtil.assembleKafkaClientProperties(request, Optional.empty());

    assertThat(props).containsEntry("auto.register.schemas", false);
  }

  @Test
  void schemaIdOfAnotherSubjectIsRejected() throws Exception {
    int foreignId = client.register("addresses-value", new AvroSchema(ADDRESS));
    var address = Map.<String, Object>of("street", "Main St", "city", "Berlin");

    // control: the same id and payload are fine under the subject the schema belongs to
    var ownSubject =
        request(strategy(SchemaSource.REGISTRY, "addresses-value", null, foreignId), address);
    assertThat(serialize(ownSubject, resolver.resolve(ownSubject))).isNotEmpty();

    var request = request(strategy(SchemaSource.REGISTRY, null, null, foreignId), address);

    assertThatThrownBy(() -> resolver.resolve(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("is not registered under subject orders-value");
    assertThat(client.getAllVersions("orders-value")).containsExactly(1, 2);
  }

  @Test
  void valueWithWrongTypeIsRejectedWhenSerializing() throws Exception {
    var valid = request(strategy(SchemaSource.REGISTRY, null, 1, null), Map.of("id", "42"));
    var wrongType = request(strategy(SchemaSource.REGISTRY, null, 1, null), Map.of("id", 42));

    assertThat(serialize(valid, resolver.resolve(valid))).isNotEmpty();
    assertThatThrownBy(() -> serialize(wrongType, resolver.resolve(wrongType)))
        .isInstanceOf(SerializationException.class)
        .hasMessageContaining("Error serializing Avro message");
    assertThat(client.getAllVersions("orders-value")).containsExactly(1, 2);
  }

  @Test
  void valueMissingARequiredFieldIsRejectedWhenSerializing() throws Exception {
    var request = request(strategy(SchemaSource.REGISTRY, null, 1, null), Map.of("other", "x"));
    var resolved = resolver.resolve(request);

    assertThatThrownBy(() -> serialize(request, resolved))
        .isInstanceOf(SerializationException.class)
        .hasMessageContaining("Error serializing Avro message");
  }

  @Test
  void inlineModeKeepsDefaultSerializerBehavior() {
    var request = request(strategy(SchemaSource.INLINE, null, null, null));

    var props = KafkaPropertiesUtil.assembleKafkaClientProperties(request, Optional.empty());

    assertThat(props)
        .doesNotContainKeys("auto.register.schemas", "use.schema.id", "use.latest.version");
  }

  @Test
  void jsonSchemaWithNestedReferencesIsSerializedAgainstTheRegistry() throws Exception {
    // order -> customer -> address -> geo
    registerJson(
        "geo-value",
        "{\"type\":\"object\",\"properties\":{\"lat\":{\"type\":\"number\"},"
            + "\"lon\":{\"type\":\"number\"}},\"required\":[\"lat\",\"lon\"]}");
    registerJson(
        "address-value",
        "{\"type\":\"object\",\"properties\":{\"street\":{\"type\":\"string\"},"
            + "\"geo\":{\"$ref\":\"geo.json\"}},\"required\":[\"street\",\"geo\"]}",
        new SchemaReference("geo.json", "geo-value", 1));
    registerJson(
        "customer-value",
        "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"},"
            + "\"address\":{\"$ref\":\"address.json\"}},\"required\":[\"name\",\"address\"]}",
        new SchemaReference("address.json", "address-value", 1),
        new SchemaReference("geo.json", "geo-value", 1));
    registerJson(
        "order-json-value",
        "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"},"
            + "\"customer\":{\"$ref\":\"customer.json\"}},\"required\":[\"id\",\"customer\"]}",
        new SchemaReference("customer.json", "customer-value", 1),
        new SchemaReference("address.json", "address-value", 1),
        new SchemaReference("geo.json", "geo-value", 1));

    var request =
        request(
            new OutboundSchemaRegistryStrategy(
                null,
                "http://registry",
                SchemaType.JSON,
                SchemaSource.REGISTRY,
                "order-json-value",
                null,
                null),
            Map.of(
                "id",
                "o-1",
                "customer",
                Map.of(
                    "name",
                    "Ada",
                    "address",
                    Map.of("street", "Main St", "geo", Map.of("lat", 52.5, "lon", 13.4)))));
    var resolved = resolver.resolve(request);

    var configs = new HashMap<String, Object>();
    KafkaPropertiesUtil.assembleKafkaClientProperties(request, resolved)
        .forEach((k, v) -> configs.put(String.valueOf(k), v));
    var record = new ProducerRecordFactory().createProducerRecord(request, resolved);
    try (var serializer = new KafkaJsonSchemaSerializer<Object>(client)) {
      serializer.configure(configs, false);
      assertThat(serializer.serialize("orders", record.value())).isNotEmpty();
    }
  }

  @Test
  void lookupClientUsesRegistryUrlOverriddenInAdditionalProperties() throws Exception {
    var urls = new ArrayList<String>();
    var capturing =
        new RegistrySchemaResolver(
            (url, configs) -> {
              urls.add(url);
              return client;
            });
    var request =
        new KafkaConnectorRequest(
            null,
            new KafkaTopic("localhost:9092", "orders"),
            new KafkaMessage("key", Map.of("id", "42")),
            strategy(SchemaSource.REGISTRY, null, null, null),
            null,
            Map.of("schema.registry.url", "http://override"));

    capturing.resolve(request);

    assertThat(urls).containsExactly("http://override");
  }

  @Test
  void lookupClientGetsTheSameSaslSettingsAsTheProducer() throws Exception {
    var captured = new ArrayList<Map<String, Object>>();
    var capturing =
        new RegistrySchemaResolver(
            (url, configs) -> {
              captured.add(configs);
              return client;
            });
    var request =
        new KafkaConnectorRequest(
            new KafkaAuthentication("user", "secret"),
            new KafkaTopic("localhost:9092", "orders"),
            new KafkaMessage("key", Map.of("id", "42")),
            strategy(SchemaSource.REGISTRY, null, null, null),
            null,
            Map.of("basic.auth.credentials.source", "SASL_INHERIT"));

    capturing.resolve(request);

    assertThat(captured).hasSize(1);
    assertThat(captured.get(0))
        .containsKey("sasl.jaas.config")
        .containsEntry("basic.auth.credentials.source", "SASL_INHERIT");
  }

  @Test
  void lookupClientAcceptsAListOfRegistryUrlsInAdditionalProperties() throws Exception {
    var urls = new ArrayList<String>();
    var capturing =
        new RegistrySchemaResolver(
            (url, configs) -> {
              urls.add(url);
              return client;
            });
    var request =
        new KafkaConnectorRequest(
            null,
            new KafkaTopic("localhost:9092", "orders"),
            new KafkaMessage("key", Map.of("id", "42")),
            strategy(SchemaSource.REGISTRY, null, null, null),
            null,
            Map.of("schema.registry.url", List.of("http://r1:8081", "http://r2:8081")));

    capturing.resolve(request);

    assertThat(urls).containsExactly("http://r1:8081,http://r2:8081");
  }

  @Test
  void lookupClientIsClosedAfterResolving() throws Exception {
    var closing = new ClosingClient();
    closing.register("orders-value", new AvroSchema(V1));
    var request = request(strategy(SchemaSource.REGISTRY, null, null, null));

    new RegistrySchemaResolver((url, configs) -> closing).resolve(request);

    assertThat(closing.closed).isTrue();
  }

  @Test
  void lookupClientIsClosedWhenResolvingFails() {
    var closing = new ClosingClient();
    var request = request(strategy(SchemaSource.REGISTRY, "missing", null, null));

    assertThatThrownBy(() -> new RegistrySchemaResolver((url, configs) -> closing).resolve(request))
        .isInstanceOf(Exception.class);
    assertThat(closing.closed).isTrue();
  }

  private static class ClosingClient extends MockSchemaRegistryClient {
    boolean closed;

    ClosingClient() {
      super(List.of(new AvroSchemaProvider(), new JsonSchemaProvider()));
    }

    @Override
    public void close() {
      closed = true;
    }
  }

  private int registerJson(String subject, String schema, SchemaReference... references)
      throws Exception {
    var parsed = client.parseSchema("JSON", schema, List.of(references)).orElseThrow();
    return client.register(subject, parsed);
  }

  private static OutboundSchemaRegistryStrategy strategy(
      SchemaSource source, String subject, Integer version, Integer id) {
    return new OutboundSchemaRegistryStrategy(
        V1, "http://registry", SchemaType.AVRO, source, subject, version, id);
  }

  private static KafkaConnectorRequest request(OutboundSchemaRegistryStrategy strategy) {
    return request(strategy, Map.of("id", "42"));
  }

  private static KafkaConnectorRequest request(
      OutboundSchemaRegistryStrategy strategy, Map<String, Object> value) {
    return new KafkaConnectorRequest(
        null,
        new KafkaTopic("localhost:9092", "orders"),
        new KafkaMessage("key", value),
        strategy,
        null,
        null);
  }

  private byte[] serialize(KafkaConnectorRequest request, Optional<ResolvedSchema> resolved)
      throws Exception {
    var record = new ProducerRecordFactory().createProducerRecord(request, resolved);
    var configs = new HashMap<String, Object>();
    KafkaPropertiesUtil.assembleKafkaClientProperties(request, resolved)
        .forEach((k, v) -> configs.put(String.valueOf(k), v));
    try (var serializer = new KafkaAvroSerializer(client)) {
      serializer.configure(configs, false);
      return serializer.serialize(request.topic().topicName(), record.value());
    }
  }
}
