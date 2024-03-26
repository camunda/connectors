/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.integration;

import static org.apache.kafka.clients.admin.AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.kafka.inbound.KafkaConnectorConsumer;
import io.camunda.connector.kafka.inbound.KafkaConnectorProperties;
import io.camunda.connector.kafka.inbound.KafkaExecutable;
import io.camunda.connector.kafka.inbound.KafkaInboundMessage;
import io.camunda.connector.kafka.model.Avro;
import io.camunda.connector.kafka.model.KafkaAuthentication;
import io.camunda.connector.kafka.model.KafkaTopic;
import io.camunda.connector.kafka.model.SerializationType;
import io.camunda.connector.kafka.outbound.KafkaConnectorFunction;
import io.camunda.connector.kafka.outbound.model.KafkaConnectorRequest;
import io.camunda.connector.kafka.outbound.model.KafkaConnectorResponse;
import io.camunda.connector.kafka.outbound.model.KafkaMessage;
import io.camunda.connector.test.inbound.InboundConnectorContextBuilder;
import io.camunda.connector.test.inbound.InboundConnectorDefinitionBuilder;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import java.net.URI;
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
import org.apache.kafka.clients.consumer.OffsetOutOfRangeException;
import org.junit.ClassRule;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.skyscreamer.jsonassert.JSONAssert;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@Disabled // to be run manually
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class KafkaIntegrationTest {

  private static final String TOPIC = "test-topic-" + UUID.randomUUID();
  private static final String AVRO_TOPIC = "avro-test-topic-" + UUID.randomUUID();
  private static final Map<String, String> HEADERS =
      Map.of("header1", "value1", "header2", "value2");

  private static String BOOTSTRAP_SERVERS;

  private static final String kafkaDockerImage = "confluentinc/cp-kafka:6.2.1";

  private static Avro avro;

  @ClassRule
  public static final KafkaContainer kafkaContainer =
      new KafkaContainer(DockerImageName.parse(kafkaDockerImage)).withReuse(true);

  @BeforeAll
  public static void init() throws Exception {
    kafkaContainer.start();
    createTopics(TOPIC, AVRO_TOPIC);
    BOOTSTRAP_SERVERS = kafkaContainer.getBootstrapServers().replace("PLAINTEXT://", "");
    URI file = ClassLoader.getSystemResource("./example-avro-schema.json").toURI();
    var avroSchema = Files.readString(Paths.get(file));
    avro = new Avro(avroSchema);
  }

  @AfterAll
  public static void cleanup() {
    kafkaContainer.stop();
  }

  private static void createTopics(String... topics) {
    var newTopics =
        Arrays.stream(topics)
            .map(topic -> new NewTopic(topic, 1, (short) 1))
            .collect(Collectors.toList());
    try (var admin = AdminClient.create(Map.of(BOOTSTRAP_SERVERS_CONFIG, getKafkaBrokers()))) {
      admin.createTopics(newTopics);
      admin.createPartitions(Map.of(TOPIC, NewPartitions.increaseTo(2)));
    }
  }

  private static String getKafkaBrokers() {
    Integer mappedPort = kafkaContainer.getFirstMappedPort();
    return String.format("%s:%d", "localhost", mappedPort);
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
            SerializationType.JSON,
            kafkaAuthentication,
            kafkaTopic,
            kafkaMessage,
            null,
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
            SerializationType.JSON,
            kafkaAuthentication,
            kafkaTopic,
            kafkaMessage,
            null,
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
            SerializationType.JSON,
            KafkaConnectorProperties.AuthenticationType.custom,
            kafkaAuthentication,
            kafkaTopic,
            null,
            null,
            List.of(9999L, 8888L),
            KafkaConnectorProperties.AutoOffsetReset.NONE,
            null);

    InboundConnectorContextBuilder.TestInboundConnectorContext context =
        InboundConnectorContextBuilder.create()
            .properties(kafkaConnectorProperties)
            .definition(InboundConnectorDefinitionBuilder.create().build())
            .build();
    KafkaExecutable executable = new KafkaExecutable();

    // When
    OffsetOutOfRangeException thrown =
        assertThrows(
            OffsetOutOfRangeException.class,
            () -> {
              try {
                executable.activate(context);
                executable.kafkaConnectorConsumer.future.get();
              } catch (Exception ex) {
                throw ex.getCause();
              }
            },
            "OffsetOutOfRangeException was expected");

    // Then we except exception with message
    assertThat(thrown.getMessage()).contains("Fetch position FetchPosition");
    assertThat(thrown.getMessage()).contains("is out of range for partition " + TOPIC + "-");
    assertEquals(0, context.getCorrelations().size());
  }

  @Test
  @Order(4)
  void consumeMessageWithInboundConnector() throws Exception {
    // Given
    KafkaTopic kafkaTopic = new KafkaTopic(BOOTSTRAP_SERVERS, TOPIC);
    KafkaAuthentication kafkaAuthentication = new KafkaAuthentication(null, null);

    KafkaConnectorProperties kafkaConnectorProperties =
        new KafkaConnectorProperties(
            SerializationType.JSON,
            KafkaConnectorProperties.AuthenticationType.custom,
            kafkaAuthentication,
            kafkaTopic,
            null,
            null,
            null,
            KafkaConnectorProperties.AutoOffsetReset.EARLIEST,
            null);

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
            SerializationType.JSON,
            KafkaConnectorProperties.AuthenticationType.custom,
            kafkaAuthentication,
            kafkaTopic,
            null,
            null,
            List.of(0L, 0L),
            KafkaConnectorProperties.AutoOffsetReset.EARLIEST,
            null);

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
    String rawValue1 = castedResult1.getRawValue();
    assertInstanceOf(String.class, rawValue1);
    JSONAssert.assertEquals("{\"message\": \"Test message\"}", rawValue1, true);
    Object value1 = castedResult1.getValue();
    assertInstanceOf(ObjectNode.class, value1);
    assertEquals("Test message", ((ObjectNode) value1).get("message").asText());

    assertInstanceOf(KafkaInboundMessage.class, context.getCorrelations().getFirst());
    KafkaInboundMessage castedResult2 = (KafkaInboundMessage) context.getCorrelations().getFirst();
    String rawValue2 = castedResult2.getRawValue();
    assertInstanceOf(String.class, rawValue2);
    assertEquals("Test message", rawValue2);
    Object value2 = castedResult2.getValue();
    assertInstanceOf(String.class, value2);
    assertEquals("Test message", value2);
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
            SerializationType.JSON,
            kafkaAuthentication,
            kafkaTopic,
            kafkaMessage,
            avro,
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
            SerializationType.AVRO,
            KafkaConnectorProperties.AuthenticationType.custom,
            kafkaAuthentication,
            kafkaTopic,
            null,
            null,
            List.of(0L, 0L),
            KafkaConnectorProperties.AutoOffsetReset.EARLIEST,
            avro);

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
            SerializationType.JSON,
            kafkaAuthentication,
            kafkaTopic,
            kafkaMessage,
            null,
            HEADERS,
            null);

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
            SerializationType.JSON,
            KafkaConnectorProperties.AuthenticationType.custom,
            kafkaAuthentication,
            kafkaTopic,
            null,
            null,
            List.of(0L, 0L),
            KafkaConnectorProperties.AutoOffsetReset.EARLIEST,
            null);

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
                  var messge = (KafkaInboundMessage) m;
                  return !(messge.getValue() instanceof String);
                })
            .findFirst()
            .orElse(null);

    assertInstanceOf(KafkaInboundMessage.class, message);
    KafkaInboundMessage castedResult1 = (KafkaInboundMessage) message;

    assertThat(castedResult1.getHeaders()).isEqualTo(HEADERS);
  }
}
