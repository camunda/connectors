/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.impl.ConnectorInputException;
import io.camunda.connector.kafka.outbound.model.KafkaConnectorRequest;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

// This test only tests different input validation and compliance, and secrets replacement
@ExtendWith(MockitoExtension.class)
class KafkaConnectorFunctionTest {

  private static final ObjectMapper objectMapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);

  private static final String SUCCESS_CASES_RESOURCE_PATH =
      "src/test/resources/requests/success-test-cases.json";
  private static final String FAIL_CASES_RESOURCE_PATH =
      "src/test/resources/requests/fail-test-cases.json";

  private static final String SECRET_USER_NAME_KEY = "USER_NAME";
  private static final String SECRET_USER_NAME = "myLogin";
  private static final String SECRET_PASSWORD_KEY = "PASSWORD";
  private static final String SECRET_PASSWORD = "mySecretPassword";
  private static final String SECRET_BOOTSTRAP_KEY = "BOOTSTRAP_SERVER";
  private static final String SECRET_BOOTSTRAP_SERVER = "kafka-stub.kafka.cloud:1234";
  private static final String SECRET_TOPIC_KEY = "TOPIC_NAME";
  private static final String SECRET_TOPIC_NAME = "some-awesome-topic";

  @Mock private KafkaProducer producer;
  @Captor private ArgumentCaptor<ProducerRecord> producerRecordCaptor;
  private KafkaConnectorFunction objectUnderTest;

  @BeforeEach
  public void before() {
    objectUnderTest = new KafkaConnectorFunction(properties -> producer);
  }

  @ParameterizedTest
  @MethodSource("successRequestCases")
  void execute_ShouldSucceedSuccessCases(final String incomingJson) throws Exception {
    // given
    CompletableFuture<RecordMetadata> completedKafkaResult = new CompletableFuture<>();
    RecordMetadata kafkaResponse =
        new RecordMetadata(new TopicPartition(SECRET_TOPIC_NAME, 1), 1, 1, 1, 1, 1);
    completedKafkaResult.complete(kafkaResponse);
    Mockito.when(producer.send(ArgumentMatchers.any())).thenReturn(completedKafkaResult);

    OutboundConnectorContext ctx =
        OutboundConnectorContextBuilder.create()
            .variables(incomingJson)
            .secret(SECRET_USER_NAME_KEY, SECRET_USER_NAME)
            .secret(SECRET_PASSWORD_KEY, SECRET_PASSWORD)
            .secret(SECRET_BOOTSTRAP_KEY, SECRET_BOOTSTRAP_SERVER)
            .secret(SECRET_TOPIC_KEY, SECRET_TOPIC_NAME)
            .build();

    // when
    objectUnderTest.execute(ctx);

    var request = ctx.bindVariables(KafkaConnectorRequest.class);

    String transformedValue =
        request.getMessage().getValue() instanceof String
            ? (String) request.getMessage().getValue()
            : objectMapper.writeValueAsString(request.getMessage().getValue());

    // then
    // Testing records are equal
    Mockito.verify(producer).send(producerRecordCaptor.capture());
    ProducerRecord recordActual = producerRecordCaptor.getValue();
    String expectedValue =
        request.getMessage().getValue() instanceof String
            ? (String) request.getMessage().getValue()
            : objectMapper.writeValueAsString(request.getMessage().getValue());
    ProducerRecord recordExpected =
        new ProducerRecord(
            request.getTopic().getTopicName(), request.getMessage().getKey(), expectedValue);
    assertThat(recordActual.toString()).isEqualTo(recordExpected.toString());

    // Testing secrets updated
    assertThat(request.getAuthentication().getUsername()).isEqualTo(SECRET_USER_NAME);
    assertThat(request.getAuthentication().getPassword()).isEqualTo(SECRET_PASSWORD);
    assertThat(request.getTopic().getBootstrapServers()).isEqualTo(SECRET_BOOTSTRAP_SERVER);
    assertThat(request.getTopic().getTopicName()).isEqualTo(SECRET_TOPIC_NAME);
  }

  @ParameterizedTest
  @MethodSource("failRequestCases")
  void execute_ShouldFail(final String incomingJson) {
    OutboundConnectorContext ctx =
        OutboundConnectorContextBuilder.create()
            .validation(new DefaultValidationProvider())
            .variables(incomingJson)
            .build();
    Assertions.assertThrows(ConnectorInputException.class, () -> objectUnderTest.execute(ctx));
  }

  @Test
  void execute_NoCredsProvided_ShouldPass() throws Exception {
    // given
    final String noAuthRequest =
        "{\n"
            + "    \"topic\":{\n"
            + "      \"bootstrapServers\":\"kafka-stub.kafka.cloud:1234\",\n"
            + "      \"topicName\":\"some-awesome-topic\"\n"
            + "    },\n"
            + "    \"message\":{\n"
            + "      \"key\":\"Happy\",\n"
            + "      \"value\":\"Case\"\n"
            + "    }\n"
            + "  }";
    CompletableFuture<RecordMetadata> completedKafkaResult = new CompletableFuture<>();
    RecordMetadata kafkaResponse =
        new RecordMetadata(new TopicPartition(SECRET_TOPIC_NAME, 1), 1, 1, 1, 1, 1);
    completedKafkaResult.complete(kafkaResponse);
    Mockito.when(producer.send(ArgumentMatchers.any())).thenReturn(completedKafkaResult);
    OutboundConnectorContext ctx =
        OutboundConnectorContextBuilder.create().variables(noAuthRequest).build();
    KafkaConnectorRequest req = ctx.bindVariables(KafkaConnectorRequest.class);

    // when
    objectUnderTest.execute(ctx);
    // then
    // Testing records are equal
    Mockito.verify(producer).send(producerRecordCaptor.capture());
    ProducerRecord recordActual = producerRecordCaptor.getValue();
    ProducerRecord recordExpected =
        new ProducerRecord(
            req.getTopic().getTopicName(), req.getMessage().getKey(), req.getMessage().getValue());
    assertThat(recordActual.toString()).isEqualTo(recordExpected.toString());

    // Testing secrets updated
    assertThat(req.getAuthentication()).isNull();
    assertThat(req.getAuthentication()).isNull();
    assertThat(req.getTopic().getBootstrapServers()).isEqualTo(SECRET_BOOTSTRAP_SERVER);
    assertThat(req.getTopic().getTopicName()).isEqualTo(SECRET_TOPIC_NAME);
  }

  private static Stream<String> successRequestCases() throws IOException {
    return loadRequestCasesFromFile(SUCCESS_CASES_RESOURCE_PATH);
  }

  private static Stream<String> failRequestCases() throws IOException {
    return loadRequestCasesFromFile(FAIL_CASES_RESOURCE_PATH);
  }

  @SuppressWarnings("unchecked")
  private static Stream<String> loadRequestCasesFromFile(final String fileName) throws IOException {
    return objectMapper
        .readValue(new File(fileName), new TypeReference<List<JsonNode>>() {})
        .stream()
        .map(JsonNode::toString);
  }
}
