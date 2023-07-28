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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.impl.inbound.result.MessageCorrelationResult;
import io.camunda.connector.kafka.inbound.KafkaConnectorProperties;
import io.camunda.connector.kafka.inbound.KafkaExecutable;
import io.camunda.connector.kafka.inbound.KafkaInboundMessage;
import io.camunda.connector.kafka.outbound.KafkaConnectorFunction;
import io.camunda.connector.kafka.outbound.model.KafkaAuthentication;
import io.camunda.connector.kafka.outbound.model.KafkaConnectorRequest;
import io.camunda.connector.kafka.outbound.model.KafkaConnectorResponse;
import io.camunda.connector.kafka.outbound.model.KafkaMessage;
import io.camunda.connector.kafka.outbound.model.KafkaTopic;
import io.camunda.connector.test.inbound.InboundConnectorContextBuilder;
import io.camunda.connector.test.inbound.InboundConnectorDefinitionBuilder;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewPartitions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.OffsetOutOfRangeException;
import org.junit.ClassRule;
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

  private static final String TOPIC = "test-topic";
  private static String BOOTSTRAP_SERVERS;

  private final String processId = "Process_id";

  private final ObjectMapper objectMapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);

  private static final String kafkaDockerImage = "confluentinc/cp-kafka:6.2.1";

  @ClassRule
  public static final KafkaContainer kafkaContainer =
      new KafkaContainer(DockerImageName.parse(kafkaDockerImage));

  @BeforeAll
  public static void init() {
    kafkaContainer.start();
    createTopics(TOPIC);
    BOOTSTRAP_SERVERS = kafkaContainer.getBootstrapServers().replace("PLAINTEXT://", "");
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

    KafkaConnectorRequest request = new KafkaConnectorRequest();
    KafkaMessage kafkaMessage = new KafkaMessage();
    kafkaMessage.setKey("1");
    kafkaMessage.setValue(Map.of("message", "Test message"));
    KafkaTopic kafkaTopic = new KafkaTopic();
    kafkaTopic.setTopicName(TOPIC);
    kafkaTopic.setBootstrapServers(BOOTSTRAP_SERVERS);
    KafkaAuthentication kafkaAuthentication = new KafkaAuthentication();
    request.setMessage(kafkaMessage);
    request.setTopic(kafkaTopic);
    request.setAuthentication(kafkaAuthentication);

    var json = objectMapper.writeValueAsString(request);

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create().variables(json).build();

    // When
    var result = function.execute(context);

    // Then
    assertInstanceOf(KafkaConnectorResponse.class, result);
    KafkaConnectorResponse castedResult = (KafkaConnectorResponse) result;
    assertEquals(TOPIC, castedResult.getTopic());
  }

  @Test
  @Order(2)
  void publishStringMessageWithOutboundConnector() throws Exception {
    // Given
    OutboundConnectorFunction function = new KafkaConnectorFunction();

    KafkaConnectorRequest request = new KafkaConnectorRequest();
    KafkaMessage kafkaMessage = new KafkaMessage();
    kafkaMessage.setKey("2");
    kafkaMessage.setValue("Test message");
    KafkaTopic kafkaTopic = new KafkaTopic();
    kafkaTopic.setTopicName(TOPIC);
    kafkaTopic.setBootstrapServers(BOOTSTRAP_SERVERS);
    KafkaAuthentication kafkaAuthentication = new KafkaAuthentication();
    request.setMessage(kafkaMessage);
    request.setTopic(kafkaTopic);
    request.setAuthentication(kafkaAuthentication);

    var json = objectMapper.writeValueAsString(request);

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create().variables(json).build();

    // When
    var result = function.execute(context);

    // Then
    assertInstanceOf(KafkaConnectorResponse.class, result);
    KafkaConnectorResponse castedResult = (KafkaConnectorResponse) result;
    assertEquals(TOPIC, castedResult.getTopic());
  }

  @Test
  @Order(3)
  void setInvalidOffsetForInboundConnectorWhenAutoOffsetResetIsNone() throws Exception {
    // Given
    KafkaTopic kafkaTopic = new KafkaTopic();
    kafkaTopic.setTopicName(TOPIC);
    kafkaTopic.setBootstrapServers(BOOTSTRAP_SERVERS);
    KafkaConnectorProperties kafkaConnectorProperties = new KafkaConnectorProperties();
    kafkaConnectorProperties.setAutoOffsetReset(KafkaConnectorProperties.AutoOffsetReset.NONE);
    kafkaConnectorProperties.setAuthenticationType(
        KafkaConnectorProperties.AuthenticationType.custom);
    kafkaConnectorProperties.setOffsets(List.of(9999L, 8888L));
    kafkaConnectorProperties.setTopic(kafkaTopic);

    InboundConnectorContextBuilder.TestInboundConnectorContext context =
        InboundConnectorContextBuilder.create()
            .result(new MessageCorrelationResult("", 0))
            .properties(kafkaConnectorProperties)
            .definition(InboundConnectorDefinitionBuilder.create().bpmnProcessId(processId).build())
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
    KafkaTopic kafkaTopic = new KafkaTopic();
    kafkaTopic.setTopicName(TOPIC);
    kafkaTopic.setBootstrapServers(BOOTSTRAP_SERVERS);
    KafkaConnectorProperties kafkaConnectorProperties = new KafkaConnectorProperties();
    kafkaConnectorProperties.setAutoOffsetReset(KafkaConnectorProperties.AutoOffsetReset.EARLIEST);
    kafkaConnectorProperties.setAuthenticationType(
        KafkaConnectorProperties.AuthenticationType.custom);
    kafkaConnectorProperties.setTopic(kafkaTopic);

    InboundConnectorContextBuilder.TestInboundConnectorContext context =
        InboundConnectorContextBuilder.create()
            .result(new MessageCorrelationResult("", 0))
            .properties(kafkaConnectorProperties)
            .definition(InboundConnectorDefinitionBuilder.create().bpmnProcessId(processId).build())
            .build();
    KafkaExecutable executable = new KafkaExecutable();

    // When
    executable.activate(context);
    await().atMost(Duration.ofSeconds(5)).until(() -> context.getCorrelations().size() > 0);
    executable.deactivate();

    // Then
    assertEquals(2, context.getCorrelations().size());
    assertInstanceOf(KafkaInboundMessage.class, context.getCorrelations().get(1));
    KafkaInboundMessage castedResult1 = (KafkaInboundMessage) context.getCorrelations().get(1);
    String rawValue1 = castedResult1.getRawValue();
    assertInstanceOf(String.class, rawValue1);
    JSONAssert.assertEquals("{\"message\": \"Test message\"}", rawValue1, true);
    Object value1 = castedResult1.getValue();
    assertInstanceOf(ObjectNode.class, value1);
    assertEquals("Test message", ((ObjectNode) value1).get("message").asText());

    assertInstanceOf(KafkaInboundMessage.class, context.getCorrelations().get(0));
    KafkaInboundMessage castedResult2 = (KafkaInboundMessage) context.getCorrelations().get(0);
    String rawValue2 = castedResult2.getRawValue();
    assertInstanceOf(String.class, rawValue2);
    assertEquals("Test message", rawValue2);
    Object value2 = castedResult2.getValue();
    assertInstanceOf(String.class, value2);
    assertEquals("Test message", value2);
  }

  @Test
  @Order(5)
  void consumeSameMessageWithInboundConnectorAgainWithOffsets() throws Exception {
    // Given
    KafkaTopic kafkaTopic = new KafkaTopic();
    kafkaTopic.setTopicName(TOPIC);
    kafkaTopic.setBootstrapServers(BOOTSTRAP_SERVERS);
    KafkaConnectorProperties kafkaConnectorProperties = new KafkaConnectorProperties();
    kafkaConnectorProperties.setAutoOffsetReset(KafkaConnectorProperties.AutoOffsetReset.EARLIEST);
    kafkaConnectorProperties.setAuthenticationType(
        KafkaConnectorProperties.AuthenticationType.custom);
    kafkaConnectorProperties.setOffsets(List.of(0L, 0L));
    kafkaConnectorProperties.setTopic(kafkaTopic);

    InboundConnectorContextBuilder.TestInboundConnectorContext context =
        InboundConnectorContextBuilder.create()
            .result(new MessageCorrelationResult("", 0))
            .properties(kafkaConnectorProperties)
            .definition(InboundConnectorDefinitionBuilder.create().bpmnProcessId(processId).build())
            .build();

    KafkaExecutable executable = new KafkaExecutable();

    // When
    executable.activate(context);
    await().atMost(Duration.ofSeconds(5)).until(() -> context.getCorrelations().size() > 0);
    executable.deactivate();

    // Then
    assertEquals(2, context.getCorrelations().size());
    assertInstanceOf(KafkaInboundMessage.class, context.getCorrelations().get(1));
    KafkaInboundMessage castedResult1 = (KafkaInboundMessage) context.getCorrelations().get(1);
    String rawValue1 = castedResult1.getRawValue();
    assertInstanceOf(String.class, rawValue1);
    JSONAssert.assertEquals("{\"message\": \"Test message\"}", rawValue1, true);
    Object value1 = castedResult1.getValue();
    assertInstanceOf(ObjectNode.class, value1);
    assertEquals("Test message", ((ObjectNode) value1).get("message").asText());

    assertInstanceOf(KafkaInboundMessage.class, context.getCorrelations().get(0));
    KafkaInboundMessage castedResult2 = (KafkaInboundMessage) context.getCorrelations().get(0);
    String rawValue2 = castedResult2.getRawValue();
    assertInstanceOf(String.class, rawValue2);
    assertEquals("Test message", rawValue2);
    Object value2 = castedResult2.getValue();
    assertInstanceOf(String.class, value2);
    assertEquals("Test message", value2);
  }
}
