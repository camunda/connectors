/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.outbound.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.kafka.model.KafkaPropertiesUtil;
import io.camunda.connector.kafka.model.KafkaTopic;
import io.camunda.connector.kafka.model.SchemaSource;
import io.camunda.connector.kafka.model.SchemaType;
import io.camunda.connector.kafka.model.schema.OutboundSchemaRegistryStrategy;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.util.HashMap;
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

  private final MockSchemaRegistryClient client = new MockSchemaRegistryClient();
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
  void schemaIdOfAnotherSubjectIsRejectedWhenSerializing() throws Exception {
    int foreignId = client.register("addresses-value", new AvroSchema(ADDRESS));
    var request = request(strategy(SchemaSource.REGISTRY, null, null, foreignId));
    var resolved = resolver.resolve(request);

    assertThatThrownBy(() -> serialize(request, resolved))
        .isInstanceOf(SerializationException.class)
        .hasMessageContaining("Error serializing Avro message");
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
