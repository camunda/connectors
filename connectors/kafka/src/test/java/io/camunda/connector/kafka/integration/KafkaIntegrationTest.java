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

import com.google.gson.Gson;
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
import io.camunda.connector.test.inbound.InboundConnectorPropertiesBuilder;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import java.time.Duration;
import java.util.Arrays;
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
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@Disabled // to be run manually
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class KafkaIntegrationTest {

  private static final String TOPIC = "my-topic";
  private static String BOOTSTRAP_SERVERS;

  private final String processId = "Process_id";

  @ClassRule
  private static final KafkaContainer kafkaContainer =
      new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:6.2.1"));

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
    kafkaMessage.setValue("{'message': 'Test message'}");
    KafkaTopic kafkaTopic = new KafkaTopic();
    kafkaTopic.setTopicName(TOPIC);
    kafkaTopic.setBootstrapServers(BOOTSTRAP_SERVERS);
    KafkaAuthentication kafkaAuthentication = new KafkaAuthentication();
    request.setMessage(kafkaMessage);
    request.setTopic(kafkaTopic);
    request.setAuthentication(kafkaAuthentication);

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create().variables(request).build();

    // When
    var result = function.execute(context);

    // Then
    assertInstanceOf(KafkaConnectorResponse.class, result);
    KafkaConnectorResponse castedResult = (KafkaConnectorResponse) result;
    assertEquals(TOPIC, castedResult.getTopic());
  }

  @Test
  @Order(2)
  void setInvalidOffsetForInboundConnectorWhenAutoOffsetResetIsNone() throws Exception {
    // Given
    KafkaTopic kafkaTopic = new KafkaTopic();
    kafkaTopic.setTopicName(TOPIC);
    kafkaTopic.setBootstrapServers(BOOTSTRAP_SERVERS);
    KafkaConnectorProperties kafkaConnectorProperties = new KafkaConnectorProperties();
    kafkaConnectorProperties.setAutoOffsetReset(KafkaConnectorProperties.AutoOffsetReset.NONE);
    kafkaConnectorProperties.setAuthenticationType("custom");
    kafkaConnectorProperties.setOffsets("9999,8888");
    kafkaConnectorProperties.setTopic(kafkaTopic);
    String jsonString =
        "{'authenticationType':'custom', "
            + "'topic.topicName':'"
            + TOPIC
            + "',"
            + "'topic.bootstrapServers':'"
            + BOOTSTRAP_SERVERS
            + "',"
            + "'autoOffsetReset':'none',"
            + "'offsets':'9999,8888'}";
    Gson gson = new Gson();
    Map<String, String> propertiesMap = gson.fromJson(jsonString, Map.class);
    InboundConnectorContextBuilder.TestInboundConnectorContext context =
        InboundConnectorContextBuilder.create()
            .result(new MessageCorrelationResult("", 0))
            .propertiesAsType(kafkaConnectorProperties)
            .properties(
                InboundConnectorPropertiesBuilder.create()
                    .properties(propertiesMap)
                    .bpmnProcessId(processId))
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
  @Order(3)
  void consumeMessageWithInboundConnector() throws Exception {
    // Given
    KafkaTopic kafkaTopic = new KafkaTopic();
    kafkaTopic.setTopicName(TOPIC);
    kafkaTopic.setBootstrapServers(BOOTSTRAP_SERVERS);
    KafkaConnectorProperties kafkaConnectorProperties = new KafkaConnectorProperties();
    kafkaConnectorProperties.setAutoOffsetReset(KafkaConnectorProperties.AutoOffsetReset.EARLIEST);
    kafkaConnectorProperties.setAuthenticationType("custom");
    kafkaConnectorProperties.setTopic(kafkaTopic);
    String jsonString =
        "{'authenticationType':'custom', "
            + "'topic.topicName':'"
            + TOPIC
            + "',"
            + "'topic.bootstrapServers':'"
            + BOOTSTRAP_SERVERS
            + "',"
            + "'autoOffsetReset':'earliest'}";
    Gson gson = new Gson();
    Map<String, String> propertiesMap = gson.fromJson(jsonString, Map.class);
    InboundConnectorContextBuilder.TestInboundConnectorContext context =
        InboundConnectorContextBuilder.create()
            .result(new MessageCorrelationResult("", 0))
            .propertiesAsType(kafkaConnectorProperties)
            .properties(
                InboundConnectorPropertiesBuilder.create()
                    .properties(propertiesMap)
                    .bpmnProcessId(processId))
            .build();
    KafkaExecutable executable = new KafkaExecutable();

    // When
    executable.activate(context);
    await().atMost(Duration.ofSeconds(5)).until(() -> context.getCorrelations().size() > 0);
    executable.deactivate();

    // Then
    assertEquals(1, context.getCorrelations().size());
    assertInstanceOf(KafkaInboundMessage.class, context.getCorrelations().get(0));
    KafkaInboundMessage castedResult = (KafkaInboundMessage) context.getCorrelations().get(0);
    String rawValue = castedResult.getRawValue();
    assertInstanceOf(String.class, rawValue);
    assertEquals("{'message': 'Test message'}", rawValue);
    Object value = castedResult.getValue();
    assertInstanceOf(Map.class, value);
    assertEquals("Test message", ((Map<String, String>) value).get("message"));
  }

  @Test
  @Order(4)
  void consumeSameMessageWithInboundConnectorAgainWithOffsets() throws Exception {
    // Given
    KafkaTopic kafkaTopic = new KafkaTopic();
    kafkaTopic.setTopicName(TOPIC);
    kafkaTopic.setBootstrapServers(BOOTSTRAP_SERVERS);
    KafkaConnectorProperties kafkaConnectorProperties = new KafkaConnectorProperties();
    kafkaConnectorProperties.setAutoOffsetReset(KafkaConnectorProperties.AutoOffsetReset.EARLIEST);
    kafkaConnectorProperties.setAuthenticationType("custom");
    kafkaConnectorProperties.setOffsets("0,0");
    kafkaConnectorProperties.setTopic(kafkaTopic);
    String jsonString =
        "{'authenticationType':'custom', "
            + "'topic.topicName':'"
            + TOPIC
            + "',"
            + "'topic.bootstrapServers':'"
            + BOOTSTRAP_SERVERS
            + "',"
            + "'autoOffsetReset':'earliest',"
            + "'offsets':'0,0'}";
    Gson gson = new Gson();
    Map<String, String> propertiesMap = gson.fromJson(jsonString, Map.class);
    InboundConnectorContextBuilder.TestInboundConnectorContext context =
        InboundConnectorContextBuilder.create()
            .result(new MessageCorrelationResult("", 0))
            .propertiesAsType(kafkaConnectorProperties)
            .properties(
                InboundConnectorPropertiesBuilder.create()
                    .properties(propertiesMap)
                    .bpmnProcessId(processId))
            .build();
    KafkaExecutable executable = new KafkaExecutable();

    // When
    executable.activate(context);
    await().atMost(Duration.ofSeconds(5)).until(() -> context.getCorrelations().size() > 0);
    executable.deactivate();

    // Then
    assertEquals(1, context.getCorrelations().size());
    assertInstanceOf(KafkaInboundMessage.class, context.getCorrelations().get(0));
    KafkaInboundMessage castedResult = (KafkaInboundMessage) context.getCorrelations().get(0);
    String rawValue = castedResult.getRawValue();
    assertInstanceOf(String.class, rawValue);
    assertEquals("{'message': 'Test message'}", rawValue);
    Object value = castedResult.getValue();
    assertInstanceOf(Map.class, value);
    assertEquals("Test message", ((Map<String, String>) value).get("message"));
  }
}
