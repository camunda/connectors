/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.integration;

import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS;
import static org.apache.kafka.clients.admin.AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.failsafe.RetryPolicy;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.kafka.inbound.KafkaConnectorConsumer;
import io.camunda.connector.kafka.inbound.KafkaConnectorProperties;
import io.camunda.connector.kafka.inbound.KafkaExecutable;
import io.camunda.connector.kafka.inbound.KafkaInboundMessage;
import io.camunda.connector.kafka.model.KafkaAuthentication;
import io.camunda.connector.kafka.model.KafkaTopic;
import io.camunda.connector.kafka.model.SchemaType;
import io.camunda.connector.kafka.model.schema.AvroInlineSchemaStrategy;
import io.camunda.connector.kafka.model.schema.InboundSchemaRegistryStrategy;
import io.camunda.connector.kafka.model.schema.NoSchemaStrategy;
import io.camunda.connector.kafka.model.schema.OutboundSchemaRegistryStrategy;
import io.camunda.connector.kafka.outbound.KafkaConnectorFunction;
import io.camunda.connector.kafka.outbound.model.KafkaConnectorRequest;
import io.camunda.connector.kafka.outbound.model.KafkaConnectorResponse;
import io.camunda.connector.kafka.outbound.model.KafkaMessage;
import io.camunda.connector.test.SlowTest;
import io.camunda.connector.test.inbound.InboundConnectorContextBuilder;
import io.camunda.connector.test.inbound.InboundConnectorDefinitionBuilder;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewPartitions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetOutOfRangeException;
import org.json.JSONException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.skyscreamer.jsonassert.JSONAssert;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

@SlowTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class KafkaIntegrationTest {

  private static final String TOPIC = "test-topic-" + UUID.randomUUID();
  private static final String AVRO_TOPIC = "avro-test-topic-" + UUID.randomUUID();
  private static final String SCHEMA_REGISTRY_AVRO_TOPIC =
      "schema-registry-avro-test-topic-" + UUID.randomUUID();
  private static final String SCHEMA_REGISTRY_JSON_TOPIC =
      "schema-registry-json-test-topic-" + UUID.randomUUID();
  private static final Map<String, String> HEADERS =
      Map.of("header1", "value1", "header2", "value2");
  private static final Network NETWORK = Network.newNetwork();

  private static final KafkaContainer kafkaContainer =
      new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.2.1"))
          .withNetwork(NETWORK)
          .withKraft();

  private static final GenericContainer<?> SCHEMA_REGISTRY =
      new GenericContainer<>(DockerImageName.parse("confluentinc/cp-schema-registry:7.5.2"))
          .withNetwork(NETWORK)
          .withExposedPorts(8081)
          .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
          .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081")
          .withEnv(
              "SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS",
              kafkaContainer.getNetworkAliases().get(0) + ":9092")
          .waitingFor(Wait.forHttp("/subjects").forStatusCode(200));
  private static final SchemaRegistryClient SCHEMA_REGISTRY_CLIENT = new SchemaRegistryClient();
  private static String BOOTSTRAP_SERVERS;
  private static String avro;
  private static String json;

  @BeforeAll
  public static void init() throws Exception {
    kafkaContainer.start();
    SCHEMA_REGISTRY.start();
    createTopics(TOPIC, AVRO_TOPIC);
    BOOTSTRAP_SERVERS = kafkaContainer.getBootstrapServers().replace("PLAINTEXT://", "");
    var avroSchema = getSchema("nested-avro-schema.json");
    var jsonSchema = getSchema("nested-json-schema.json");
    avro = avroSchema;
    json = jsonSchema;
    // CREATE Another Avro containing the JsonSchema
    var responses =
        SCHEMA_REGISTRY_CLIENT.registerAll(
            List.of(
                new SchemaRegistryClient.SchemaWithTopic(avroSchema, SCHEMA_REGISTRY_AVRO_TOPIC),
                new SchemaRegistryClient.SchemaWithTopic(jsonSchema, SCHEMA_REGISTRY_JSON_TOPIC)),
            SCHEMA_REGISTRY.getHost() + ":" + SCHEMA_REGISTRY.getFirstMappedPort());
    assertThat(responses).isNotNull();
    assertThat(responses).hasSize(2);
    assertThat(responses.get(0)).contains("id");
    assertThat(responses.get(1)).contains("id");
    var subjects =
        SCHEMA_REGISTRY_CLIENT.getSubjects(
            SCHEMA_REGISTRY.getHost() + ":" + SCHEMA_REGISTRY.getFirstMappedPort());
    System.out.println(subjects);
  }

  @AfterAll
  public static void cleanup() {
    SCHEMA_REGISTRY.stop();
    kafkaContainer.stop();
  }

  private static void createTopics(String... topics) {
    var newTopics =
        Arrays.stream(topics)
            .map(topic -> new NewTopic(topic, 1, (short) 1))
            .collect(Collectors.toList());
    try (var admin = AdminClient.create(Map.of(BOOTSTRAP_SERVERS_CONFIG, getKafkaBrokers()))) {
      admin.createTopics(newTopics);
      Arrays.stream(topics)
          .forEach(topic -> admin.createPartitions(Map.of(topic, NewPartitions.increaseTo(2))));
    }
  }

  private static String getKafkaBrokers() {
    Integer mappedPort = kafkaContainer.getFirstMappedPort();
    return String.format("%s:%d", "localhost", mappedPort);
  }

  private static String getSchema(String schemaName) throws URISyntaxException {
    URI file = ClassLoader.getSystemResource(schemaName).toURI();
    try {
      return Files.readString(Paths.get(file));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void assertMessage(KafkaInboundMessage castedResult1) throws JSONException {
    if (((IntNode) (castedResult1.getKey())).asText().equals("1")) {
      assertObjectMessage(castedResult1);
    } else {
      assertStringMessage(castedResult1);
    }
  }

  private void assertStringMessage(KafkaInboundMessage castedResult2) {
    String rawValue2 = castedResult2.getRawValue();
    assertInstanceOf(String.class, rawValue2);
    assertEquals("Test message", rawValue2);
    Object value2 = castedResult2.getValue();
    assertInstanceOf(String.class, value2);
    assertEquals("Test message", value2);
  }

  private void assertObjectMessage(KafkaInboundMessage castedResult1) throws JSONException {
    String rawValue1 = castedResult1.getRawValue();
    assertInstanceOf(String.class, rawValue1);
    JSONAssert.assertEquals("{\"message\": \"Test message\"}", rawValue1, true);
    Object value1 = castedResult1.getValue();
    assertInstanceOf(ObjectNode.class, value1);
    assertEquals("Test message", ((ObjectNode) value1).get("message").asText());
  }

  @Test
  @Order(1)
  void publishMessageWithOutboundConnector() throws Exception {
    // Given
    OutboundConnectorFunction function = new KafkaConnectorFunction();

    KafkaMessage kafkaMessage = new KafkaMessage("1", Map.of("message", "Test message"));
    KafkaTopic kafkaTopic = new KafkaTopic(BOOTSTRAP_SERVERS, TOPIC);
    KafkaAuthentication kafkaAuthentication = new KafkaAuthentication(null, null);
    KafkaConnectorRequest request =
        new KafkaConnectorRequest(
            kafkaAuthentication, kafkaTopic, kafkaMessage, new NoSchemaStrategy(), null, null);

    var json = KafkaConnectorConsumer.objectMapper.writeValueAsString(request);

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create().variables(json).build();

    // When
    var result = function.execute(context);

    // Then
    assertInstanceOf(KafkaConnectorResponse.class, result);
    KafkaConnectorResponse castedResult = (KafkaConnectorResponse) result;
    assertEquals(TOPIC, castedResult.topic());
  }

  @Test
  @Order(2)
  void publishStringMessageWithOutboundConnector() throws Exception {
    // Given
    OutboundConnectorFunction function = new KafkaConnectorFunction();

    KafkaMessage kafkaMessage = new KafkaMessage("2", "Test message");
    KafkaTopic kafkaTopic = new KafkaTopic(BOOTSTRAP_SERVERS, TOPIC);
    KafkaAuthentication kafkaAuthentication = new KafkaAuthentication(null, null);

    KafkaConnectorRequest request =
        new KafkaConnectorRequest(
            kafkaAuthentication, kafkaTopic, kafkaMessage, new NoSchemaStrategy(), null, null);

    var json = KafkaConnectorConsumer.objectMapper.writeValueAsString(request);

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create().variables(json).build();

    // When
    var result = function.execute(context);

    // Then
    assertInstanceOf(KafkaConnectorResponse.class, result);
    KafkaConnectorResponse castedResult = (KafkaConnectorResponse) result;
    assertEquals(TOPIC, castedResult.topic());
  }

  @Test
  @Order(3)
  void setInvalidOffsetForInboundConnectorWhenAutoOffsetResetIsNone() {
    // Given
    KafkaTopic kafkaTopic = new KafkaTopic(BOOTSTRAP_SERVERS, TOPIC);
    KafkaAuthentication kafkaAuthentication = new KafkaAuthentication(null, null);

    KafkaConnectorProperties kafkaConnectorProperties =
        new KafkaConnectorProperties(
            KafkaConnectorProperties.AuthenticationType.custom,
            kafkaAuthentication,
            kafkaTopic,
            null,
            null,
            List.of(9999L, 8888L),
            KafkaConnectorProperties.AutoOffsetReset.NONE,
            new NoSchemaStrategy());

    InboundConnectorContextBuilder.TestInboundConnectorContext context =
        InboundConnectorContextBuilder.create()
            .properties(kafkaConnectorProperties)
            .definition(InboundConnectorDefinitionBuilder.create().build())
            .build();
    KafkaExecutable executable =
        new KafkaExecutable(KafkaConsumer::new, RetryPolicy.builder().withMaxAttempts(1).build());

    // When
    Exception thrown =
        assertThrows(
            Exception.class,
            () -> {
              try {
                executable.activate(context);
                executable.kafkaConnectorConsumer.future.get();
              } catch (Exception ex) {
                throw ex.getCause();
              }
            },
            "OffsetOutOfRangeException was expected");

    assertThat(thrown.getCause()).isInstanceOf(OffsetOutOfRangeException.class);

    // Then we except exception with message
    assertThat(thrown.getMessage()).contains("Fetch position FetchPosition");
    assertThat(thrown.getMessage()).contains("is out of range for partition " + TOPIC + "-");
    assertEquals(0, context.getCorrelations().size());
    executable.deactivate();
  }

  @Test
  @Order(4)
  void consumeMessageWithInboundConnector() throws Exception {
    // Given
    KafkaTopic kafkaTopic = new KafkaTopic(BOOTSTRAP_SERVERS, TOPIC);
    KafkaAuthentication kafkaAuthentication = new KafkaAuthentication(null, null);

    KafkaConnectorProperties kafkaConnectorProperties =
        new KafkaConnectorProperties(
            KafkaConnectorProperties.AuthenticationType.custom,
            kafkaAuthentication,
            kafkaTopic,
            null,
            null,
            null,
            KafkaConnectorProperties.AutoOffsetReset.EARLIEST,
            new NoSchemaStrategy());

    InboundConnectorContextBuilder.TestInboundConnectorContext context =
        InboundConnectorContextBuilder.create()
            .properties(kafkaConnectorProperties)
            .definition(InboundConnectorDefinitionBuilder.create().build())
            .build();
    KafkaExecutable executable = new KafkaExecutable();

    // When
    executable.activate(context);
    await().atMost(Duration.ofSeconds(55)).until(() -> !context.getCorrelations().isEmpty());
    executable.deactivate();

    // Then
    assertEquals(2, context.getCorrelations().size());
    var message =
        context.getCorrelations().stream()
            .filter(
                m -> {
                  var messge = (KafkaInboundMessage) m;
                  return !(messge.getValue() instanceof String);
                })
            .findFirst()
            .orElse(null);

    assertInstanceOf(KafkaInboundMessage.class, message);
    KafkaInboundMessage castedResult1 = (KafkaInboundMessage) message;

    String rawValue1 = castedResult1.getRawValue();
    JSONAssert.assertEquals("{\"message\": \"Test message\"}", rawValue1, true);
  }

  @Test
  @Order(5)
  void consumeSameMessageWithInboundConnectorAgainWithOffsets() throws Exception {
    // Given
    KafkaTopic kafkaTopic = new KafkaTopic(BOOTSTRAP_SERVERS, TOPIC);

    KafkaAuthentication kafkaAuthentication = new KafkaAuthentication(null, null);
    KafkaConnectorProperties kafkaConnectorProperties =
        new KafkaConnectorProperties(
            KafkaConnectorProperties.AuthenticationType.custom,
            kafkaAuthentication,
            kafkaTopic,
            null,
            null,
            List.of(0L, 0L),
            KafkaConnectorProperties.AutoOffsetReset.EARLIEST,
            new NoSchemaStrategy());

    InboundConnectorContextBuilder.TestInboundConnectorContext context =
        InboundConnectorContextBuilder.create()
            .properties(kafkaConnectorProperties)
            .definition(InboundConnectorDefinitionBuilder.create().build())
            .build();

    KafkaExecutable executable = new KafkaExecutable();

    // When
    executable.activate(context);
    await().atMost(Duration.ofSeconds(5)).until(() -> !context.getCorrelations().isEmpty());
    executable.deactivate();

    // Then
    var inboundMessage = context.getCorrelations().getLast();
    assertInstanceOf(KafkaInboundMessage.class, inboundMessage);
    KafkaInboundMessage castedResult1 = (KafkaInboundMessage) inboundMessage;
    assertMessage(castedResult1);

    assertInstanceOf(KafkaInboundMessage.class, context.getCorrelations().getFirst());
    KafkaInboundMessage castedResult2 = (KafkaInboundMessage) context.getCorrelations().getFirst();
    assertMessage(castedResult2);
  }

  @Test
  @Order(6)
  void publishAvroMessage() throws Exception {
    // Given
    OutboundConnectorFunction function = new KafkaConnectorFunction();

    KafkaMessage kafkaMessage =
        new KafkaMessage(
            "avro1", Map.of("name", "Test", "age", 40, "emails", List.of("test@camunda.com")));
    KafkaTopic kafkaTopic = new KafkaTopic(BOOTSTRAP_SERVERS, AVRO_TOPIC);

    KafkaAuthentication kafkaAuthentication = new KafkaAuthentication(null, null);
    KafkaConnectorRequest request =
        new KafkaConnectorRequest(
            kafkaAuthentication,
            kafkaTopic,
            kafkaMessage,
            new AvroInlineSchemaStrategy(avro),
            null,
            null);

    var json = KafkaConnectorConsumer.objectMapper.writeValueAsString(request);

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create().variables(json).build();

    // When
    var result = function.execute(context);

    // Then
    assertInstanceOf(KafkaConnectorResponse.class, result);
    KafkaConnectorResponse castedResult = (KafkaConnectorResponse) result;
    assertEquals(AVRO_TOPIC, castedResult.topic());
  }

  @Test
  @Order(7)
  void consumeAvro() {
    // Given
    KafkaTopic kafkaTopic = new KafkaTopic(BOOTSTRAP_SERVERS, AVRO_TOPIC);

    KafkaAuthentication kafkaAuthentication = new KafkaAuthentication(null, null);
    KafkaConnectorProperties kafkaConnectorProperties =
        new KafkaConnectorProperties(
            KafkaConnectorProperties.AuthenticationType.custom,
            kafkaAuthentication,
            kafkaTopic,
            null,
            null,
            List.of(0L, 0L),
            KafkaConnectorProperties.AutoOffsetReset.EARLIEST,
            new AvroInlineSchemaStrategy(avro));

    InboundConnectorContextBuilder.TestInboundConnectorContext context =
        InboundConnectorContextBuilder.create()
            .properties(kafkaConnectorProperties)
            .definition(InboundConnectorDefinitionBuilder.create().build())
            .build();

    KafkaExecutable executable = new KafkaExecutable();

    // When
    executable.activate(context);
    await().atMost(Duration.ofSeconds(5)).until(() -> !context.getCorrelations().isEmpty());
    executable.deactivate();

    // Then
    var inboundMessage = context.getCorrelations().stream().findFirst().orElse(null);
    assertInstanceOf(KafkaInboundMessage.class, inboundMessage);
    KafkaInboundMessage castedResult1 = (KafkaInboundMessage) inboundMessage;

    Object value1 = castedResult1.getValue();
    assertInstanceOf(ObjectNode.class, value1);
    assertEquals("Test", ((ObjectNode) value1).get("name").asText());
    assertEquals(40, ((ObjectNode) value1).get("age").asInt());
    assertEquals(
        "test@camunda.com", ((ObjectNode) value1).get("emails").elements().next().asText());
  }

  @Test
  @Order(8)
  void publishMessageWithHeaders() throws Exception {
    // Given
    OutboundConnectorFunction function = new KafkaConnectorFunction();

    KafkaMessage kafkaMessage = new KafkaMessage("8", Map.of("message", "Test message"));
    KafkaTopic kafkaTopic = new KafkaTopic(BOOTSTRAP_SERVERS, TOPIC);
    KafkaAuthentication kafkaAuthentication = new KafkaAuthentication(null, null);
    KafkaConnectorRequest request =
        new KafkaConnectorRequest(
            kafkaAuthentication, kafkaTopic, kafkaMessage, new NoSchemaStrategy(), HEADERS, null);

    var json = KafkaConnectorConsumer.objectMapper.writeValueAsString(request);

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create().variables(json).build();
    // When
    var result = function.execute(context);
    // Then
    assertInstanceOf(KafkaConnectorResponse.class, result);
    // check headers in next test case
  }

  @Test
  @Order(9)
  void consumeMessageWithHeaders() {
    // Given
    KafkaTopic kafkaTopic = new KafkaTopic(BOOTSTRAP_SERVERS, TOPIC);

    KafkaAuthentication kafkaAuthentication = new KafkaAuthentication(null, null);
    KafkaConnectorProperties kafkaConnectorProperties =
        new KafkaConnectorProperties(
            KafkaConnectorProperties.AuthenticationType.custom,
            kafkaAuthentication,
            kafkaTopic,
            null,
            null,
            null,
            KafkaConnectorProperties.AutoOffsetReset.EARLIEST,
            new NoSchemaStrategy());

    InboundConnectorContextBuilder.TestInboundConnectorContext context =
        InboundConnectorContextBuilder.create()
            .properties(kafkaConnectorProperties)
            .definition(InboundConnectorDefinitionBuilder.create().build())
            .build();
    KafkaExecutable executable = new KafkaExecutable();

    // When
    executable.activate(context);
    await().atMost(Duration.ofSeconds(5)).until(() -> !context.getCorrelations().isEmpty());
    executable.deactivate();

    // Then
    var message =
        context.getCorrelations().stream()
            .filter(
                m -> {
                  var msg = (KafkaInboundMessage) m;
                  return !(msg.getValue() instanceof String);
                })
            .findFirst()
            .orElse(null);

    assertInstanceOf(KafkaInboundMessage.class, message);
    KafkaInboundMessage castedResult1 = (KafkaInboundMessage) message;

    assertThat(castedResult1.getHeaders()).isEqualTo(HEADERS);
  }

  @Test
  @Order(10)
  void publishSchemaRegistryAvroMessage() throws Exception {
    // Given
    OutboundConnectorFunction function = new KafkaConnectorFunction();

    KafkaMessage kafkaMessage =
        new KafkaMessage(
            null,
            Map.of(
                "colleagues",
                List.of(
                    Map.of(
                        "name", "Colleague1", "age", 30, "emails", List.of("test2@camunda.com"))),
                "name",
                "Test",
                "nickname",
                "theNickname",
                "age",
                40,
                "emails",
                List.of("test@camunda.com"),
                "boss",
                Map.of("name", "Boss", "position", "CEO")));
    KafkaTopic kafkaTopic = new KafkaTopic(BOOTSTRAP_SERVERS, SCHEMA_REGISTRY_AVRO_TOPIC);

    KafkaAuthentication kafkaAuthentication = new KafkaAuthentication(null, null);
    KafkaConnectorRequest request =
        new KafkaConnectorRequest(
            kafkaAuthentication,
            kafkaTopic,
            kafkaMessage,
            new OutboundSchemaRegistryStrategy(
                avro,
                "http://" + SCHEMA_REGISTRY.getHost() + ":" + SCHEMA_REGISTRY.getFirstMappedPort(),
                SchemaType.AVRO),
            null,
            Map.of(AUTO_REGISTER_SCHEMAS, false));

    var json = KafkaConnectorConsumer.objectMapper.writeValueAsString(request);

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create().variables(json).build();

    // When
    var result = function.execute(context);

    // Then
    assertInstanceOf(KafkaConnectorResponse.class, result);
    KafkaConnectorResponse castedResult = (KafkaConnectorResponse) result;
    assertEquals(SCHEMA_REGISTRY_AVRO_TOPIC, castedResult.topic());
  }

  @Test
  @Order(11)
  void consumeSchemaRegistryAvroMessage() throws Exception {
    // Given
    var kafkaTopic = new KafkaTopic(BOOTSTRAP_SERVERS, SCHEMA_REGISTRY_AVRO_TOPIC);
    var kafkaAuthentication = new KafkaAuthentication(null, null);
    KafkaConnectorProperties kafkaConnectorProperties =
        new KafkaConnectorProperties(
            KafkaConnectorProperties.AuthenticationType.custom,
            kafkaAuthentication,
            kafkaTopic,
            null,
            null,
            null,
            KafkaConnectorProperties.AutoOffsetReset.EARLIEST,
            new InboundSchemaRegistryStrategy(
                "http://" + SCHEMA_REGISTRY.getHost() + ":" + SCHEMA_REGISTRY.getFirstMappedPort(),
                SchemaType.AVRO));

    InboundConnectorContextBuilder.TestInboundConnectorContext context2 =
        InboundConnectorContextBuilder.create()
            .properties(kafkaConnectorProperties)
            .definition(InboundConnectorDefinitionBuilder.create().build())
            .build();

    KafkaExecutable executable = new KafkaExecutable();

    // When
    executable.activate(context2);
    await().atMost(Duration.ofSeconds(15)).until(() -> !context2.getCorrelations().isEmpty());
    executable.deactivate();

    // Then
    var inboundMessage = context2.getCorrelations().stream().findFirst().orElse(null);
    assertInstanceOf(KafkaInboundMessage.class, inboundMessage);
    KafkaInboundMessage castedResult1 = (KafkaInboundMessage) inboundMessage;

    Object value1 = castedResult1.getValue();
    var rawValue = castedResult1.getRawValue();
    assertNull(rawValue);
    assertInstanceOf(ObjectNode.class, value1);
    String json = ConnectorsObjectMapperSupplier.getCopy().writeValueAsString(value1);
    Map map = ConnectorsObjectMapperSupplier.getCopy().readValue(json, Map.class);
    assertEquals("Test", map.get("name").toString());
    assertEquals(40, map.get("age"));
    assertEquals("test@camunda.com", ((List) map.get("emails")).get(0));
    assertEquals("Boss", ((Map) map.get("boss")).get("name"));
    assertEquals("CEO", ((Map) map.get("boss")).get("position"));
    assertEquals("theNickname", map.get("nickname"));
    assertEquals("Colleague1", ((Map) ((List) map.get("colleagues")).get(0)).get("name"));
    assertEquals(30, ((Map) ((List) map.get("colleagues")).get(0)).get("age"));
  }

  @Test
  @Order(12)
  void publishSchemaRegistryJsonMessage() throws Exception {
    // Given
    OutboundConnectorFunction function = new KafkaConnectorFunction();

    KafkaMessage kafkaMessage =
        new KafkaMessage(
            null,
            Map.of(
                "colleagues",
                List.of(
                    Map.of(
                        "name", "Colleague1", "age", 30, "emails", List.of("test2@camunda.com"))),
                "name",
                "Test",
                "nickname",
                "theNickname",
                "age",
                40,
                "emails",
                List.of("test@camunda.com"),
                "boss",
                Map.of("name", "Boss", "position", "CEO")));
    KafkaTopic kafkaTopic = new KafkaTopic(BOOTSTRAP_SERVERS, SCHEMA_REGISTRY_JSON_TOPIC);

    KafkaAuthentication kafkaAuthentication = new KafkaAuthentication(null, null);
    KafkaConnectorRequest request =
        new KafkaConnectorRequest(
            kafkaAuthentication,
            kafkaTopic,
            kafkaMessage,
            new OutboundSchemaRegistryStrategy(
                json,
                "http://" + SCHEMA_REGISTRY.getHost() + ":" + SCHEMA_REGISTRY.getFirstMappedPort(),
                SchemaType.JSON),
            null,
            Map.of(AUTO_REGISTER_SCHEMAS, false));

    var json = KafkaConnectorConsumer.objectMapper.writeValueAsString(request);

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create().variables(json).build();

    // When
    var result = function.execute(context);

    // Then
    assertInstanceOf(KafkaConnectorResponse.class, result);
    KafkaConnectorResponse castedResult = (KafkaConnectorResponse) result;
    assertEquals(SCHEMA_REGISTRY_JSON_TOPIC, castedResult.topic());
  }

  @Test
  @Order(13)
  void consumeSchemaRegistryJsonMessage() throws Exception {
    // Given
    var kafkaTopic = new KafkaTopic(BOOTSTRAP_SERVERS, SCHEMA_REGISTRY_JSON_TOPIC);
    var kafkaAuthentication = new KafkaAuthentication(null, null);
    KafkaConnectorProperties kafkaConnectorProperties =
        new KafkaConnectorProperties(
            KafkaConnectorProperties.AuthenticationType.custom,
            kafkaAuthentication,
            kafkaTopic,
            null,
            null,
            null,
            KafkaConnectorProperties.AutoOffsetReset.EARLIEST,
            new InboundSchemaRegistryStrategy(
                "http://" + SCHEMA_REGISTRY.getHost() + ":" + SCHEMA_REGISTRY.getFirstMappedPort(),
                SchemaType.JSON));

    InboundConnectorContextBuilder.TestInboundConnectorContext context2 =
        InboundConnectorContextBuilder.create()
            .properties(kafkaConnectorProperties)
            .definition(InboundConnectorDefinitionBuilder.create().build())
            .build();

    KafkaExecutable executable = new KafkaExecutable();

    // When
    executable.activate(context2);
    await().atMost(Duration.ofSeconds(15)).until(() -> !context2.getCorrelations().isEmpty());
    executable.deactivate();

    // Then
    var inboundMessage = context2.getCorrelations().stream().findFirst().orElse(null);
    assertInstanceOf(KafkaInboundMessage.class, inboundMessage);
    KafkaInboundMessage castedResult1 = (KafkaInboundMessage) inboundMessage;

    Object value1 = castedResult1.getValue();
    var rawValue = castedResult1.getRawValue();
    assertInstanceOf(JsonNode.class, value1);
    assertInstanceOf(String.class, rawValue);
    String json = ConnectorsObjectMapperSupplier.getCopy().writeValueAsString(value1);
    Map map = ConnectorsObjectMapperSupplier.getCopy().readValue(json, Map.class);
    assertEquals(json, rawValue);
    assertEquals("Test", map.get("name").toString());
    assertEquals(40, map.get("age"));
    assertEquals("test@camunda.com", ((List) map.get("emails")).get(0));
    assertEquals("Boss", ((Map) map.get("boss")).get("name"));
    assertEquals("CEO", ((Map) map.get("boss")).get("position"));
    assertEquals("theNickname", map.get("nickname"));
    assertEquals("Colleague1", ((Map) ((List) map.get("colleagues")).get(0)).get("name"));
    assertEquals(30, ((Map) ((List) map.get("colleagues")).get(0)).get("age"));
  }
}
