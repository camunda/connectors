/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.inbound;

import static io.camunda.connector.kafka.inbound.KafkaPropertyTransformer.DEFAULT_KEY_DESERIALIZER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.failsafe.RetryPolicy;
import io.camunda.connector.kafka.model.KafkaAuthentication;
import io.camunda.connector.kafka.model.KafkaTopic;
import io.camunda.connector.kafka.model.schema.NoSchemaStrategy;
import io.camunda.connector.runtime.test.inbound.InboundConnectorContextBuilder;
import io.camunda.connector.runtime.test.inbound.InboundConnectorDefinitionBuilder;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class KafkaExecutableTest {

  private static final int MAX_ATTEMPTS = 3;
  private static final int INFINITE_RETRIES = -1;
  private InboundConnectorContextBuilder.TestInboundConnectorContext context;
  private InboundConnectorContextBuilder.TestInboundConnectorContext originalContext;
  private KafkaConnectorProperties kafkaConnectorProperties;
  private KafkaTopic kafkaTopic;
  private KafkaConsumer<Object, Object> mockConsumer;

  private static Stream<Arguments> provideStringsForGetOffsets() {
    return Stream.of(
        Arguments.of("=[1,2,3]", List.of(1L, 2L, 3L)),
        Arguments.of("[1,2,3]", List.of(1L, 2L, 3L)),
        Arguments.of("1", List.of(1L)),
        Arguments.of("1,2", Arrays.asList(1L, 2L)),
        Arguments.of("1,2,3,", Arrays.asList(1L, 2L, 3L)),
        Arguments.of("1,2,3,4,5", Arrays.asList(1L, 2L, 3L, 4L, 5L)),
        Arguments.of(Arrays.asList(10L, 12L), Arrays.asList(10L, 12L)));
  }

  @BeforeEach
  @SuppressWarnings("unchecked")
  public void setUp() {
    String topic = "my-topic";
    kafkaTopic = new KafkaTopic("localhost:9092", topic);
    KafkaAuthentication kafkaAuthentication = new KafkaAuthentication(null, null);
    kafkaConnectorProperties =
        new KafkaConnectorProperties(
            KafkaConnectorProperties.AuthenticationType.custom,
            kafkaAuthentication,
            kafkaTopic,
            null,
            null,
            null,
            KafkaConnectorProperties.AutoOffsetReset.NONE,
            new NoSchemaStrategy());

    context =
        InboundConnectorContextBuilder.create()
            .secret("test", "test")
            .properties(kafkaConnectorProperties)
            .definition(InboundConnectorDefinitionBuilder.create().build())
            .validation(new DefaultValidationProvider())
            .build();
    originalContext = context;
    mockConsumer = mock(KafkaConsumer.class);
  }

  @Test
  public void testActivateMainFunctionality() throws Exception {
    KafkaExecutable kafkaExecutable = getConsumerMock();

    // Return and stop looping
    when(mockConsumer.poll(any()))
        .then(
            invocationOnMock -> {
              kafkaExecutable.kafkaConnectorConsumer.shouldLoop = false;
              return new ConsumerRecords<>(new HashMap<>());
            });

    var groupMetadataMock = mock(ConsumerGroupMetadata.class);
    when(groupMetadataMock.groupId()).thenReturn("groupId");
    when(groupMetadataMock.groupInstanceId()).thenReturn(Optional.of("groupInstanceId"));
    when(groupMetadataMock.generationId()).thenReturn(1);
    when(mockConsumer.groupMetadata()).thenReturn(groupMetadataMock);

    // When
    kafkaExecutable.activate(context);

    // Then
    assertNotNull(kafkaExecutable.kafkaConnectorConsumer.future);
    kafkaExecutable.kafkaConnectorConsumer.future.get(3, TimeUnit.SECONDS);
    assertEquals(originalContext, context);
    // Create an ArgumentCaptor to capture the argument passed to subscribe
    ArgumentCaptor<Collection<String>> argumentCaptor = ArgumentCaptor.forClass(Collection.class);
    verify(mockConsumer, times(1)).subscribe(argumentCaptor.capture(), any());
    verify(mockConsumer, times(1)).poll(any());
  }

  @Test
  void testActivateAndDeactivate() {
    // Given
    doNothing().when(mockConsumer).subscribe(anyCollection(), any());
    KafkaExecutable kafkaExecutable = getConsumerMock();

    // When
    kafkaExecutable.activate(context);
    kafkaExecutable.deactivate();

    // Then
    assertEquals(originalContext, context);
    assertFalse(kafkaExecutable.kafkaConnectorConsumer.shouldLoop);
  }

  @Test
  void testActivateAndDeactivate_consumerThrows() {
    // Given
    doNothing().when(mockConsumer).subscribe(anyCollection(), any());
    KafkaExecutable kafkaExecutable = getConsumerMock();
    var groupMetadataMock = mock(ConsumerGroupMetadata.class);
    when(groupMetadataMock.groupId()).thenReturn("groupId");
    when(groupMetadataMock.groupInstanceId()).thenReturn(Optional.of("groupInstanceId"));
    when(groupMetadataMock.generationId()).thenReturn(1);
    when(mockConsumer.groupMetadata()).thenReturn(groupMetadataMock);

    // When
    when(mockConsumer.poll(any())).thenThrow(new RuntimeException("Test exception"));
    kafkaExecutable.activate(context);
    await()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(() -> assertFalse(kafkaExecutable.kafkaConnectorConsumer.shouldLoop));

    // Then
    verify(mockConsumer, times(MAX_ATTEMPTS)).subscribe(anyCollection(), any());
    verify(mockConsumer, times(MAX_ATTEMPTS)).poll(any(Duration.class));
    verify(mockConsumer, times(MAX_ATTEMPTS)).close();
  }

  @Test
  void testActivateAndDeactivate_consumerThrowsWithInfiniteRetries() {
    // Given
    KafkaExecutable kafkaExecutable = getThrowingConsumerMock();

    // When
    kafkaExecutable.activate(context);

    // then
    Assertions.assertTimeoutPreemptively(
        Duration.ofSeconds(15),
        () -> {
          kafkaExecutable.deactivate();
        },
        "Deactivate should not hang, it's likely an issue with the stopConsumer method");
  }

  @Test
  void testGetKafkaProperties() {
    // When
    Properties properties =
        KafkaPropertyTransformer.getKafkaProperties(kafkaConnectorProperties, context);
    // Then
    assertEquals("localhost:9092", properties.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG));
    assertEquals(
        DEFAULT_KEY_DESERIALIZER, properties.get(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG));
    assertEquals(
        DEFAULT_KEY_DESERIALIZER, properties.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG));
    assertEquals(false, properties.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG));

    assertEquals(
        "kafka-inbound-connector-" + context.getDefinition().deduplicationId(),
        properties.get(ConsumerConfig.GROUP_ID_CONFIG));
  }

  @Test
  void testGroupIdUsage() {
    // When
    KafkaAuthentication kafkaAuthentication = new KafkaAuthentication(null, null);
    KafkaConnectorProperties kafkaConnectorProperties =
        new KafkaConnectorProperties(
            KafkaConnectorProperties.AuthenticationType.custom,
            kafkaAuthentication,
            kafkaTopic,
            "my-group-id",
            null,
            List.of(0L, 0L),
            KafkaConnectorProperties.AutoOffsetReset.EARLIEST,
            new NoSchemaStrategy());
    Properties properties =
        KafkaPropertyTransformer.getKafkaProperties(kafkaConnectorProperties, context);
    // Then
    assertEquals("my-group-id", properties.get(ConsumerConfig.GROUP_ID_CONFIG));
  }

  @Test
  public void testConvertConsumerRecordToKafkaInboundMessage() {
    // When
    ConsumerRecord<Object, Object> consumerRecord =
        new ConsumerRecord<>("my-topic", 0, 0, "my-key", "{\"foo\": \"bar\"}");
    consumerRecord.headers().add("header", "headerValue".getBytes());
    KafkaInboundMessage kafkaInboundMessage =
        KafkaPropertyTransformer.convertConsumerRecordToKafkaInboundMessage(
            consumerRecord, KafkaConnectorConsumer.objectMapper.reader());

    // Then
    assertEquals("my-key", kafkaInboundMessage.getKey());
    assertEquals("{\"foo\": \"bar\"}", kafkaInboundMessage.getRawValue());
    ObjectNode expectedValue = JsonNodeFactory.instance.objectNode();
    expectedValue.set("foo", JsonNodeFactory.instance.textNode("bar"));
    assertEquals(expectedValue, kafkaInboundMessage.getValue());
    assertEquals("headerValue", kafkaInboundMessage.getHeaders().get("header"));
  }

  @Test
  public void testConvertSpecialCharactersRecordToKafkaInboundMessage() {
    // When
    ConsumerRecord<Object, Object> consumerRecord =
        new ConsumerRecord<>("my-topic", 0, 0, "my-key", "{\"foo\": \"\nb\ta\\r\"}");
    KafkaInboundMessage kafkaInboundMessage =
        KafkaPropertyTransformer.convertConsumerRecordToKafkaInboundMessage(
            consumerRecord, KafkaConnectorConsumer.objectMapper.reader());

    // Then
    assertEquals("my-key", kafkaInboundMessage.getKey());
    assertEquals("{\"foo\": \"\nb\ta\\r\"}", kafkaInboundMessage.getRawValue());
    ObjectNode expectedValue = JsonNodeFactory.instance.objectNode();
    expectedValue.set("foo", JsonNodeFactory.instance.textNode("\nb\ta\r"));
    assertEquals(expectedValue, kafkaInboundMessage.getValue());
  }

  @Test
  public void testConvertDoubleEscapedCharactersRecordToKafkaInboundMessage() {
    // When
    String kafkaMessageValue =
        "{\"ordertime\":1497014222380,\"orderid\":18,\"itemid\":\"Item_184\",\"message\":\"{\\\"valid\\\":true}\",\"address\":{\"city\":\"Mountain View\",\"state\":\"CA\",\"zipcode\":94041}}";
    ConsumerRecord<Object, Object> consumerRecord =
        new ConsumerRecord<>("my-topic", 0, 0, "my-key", kafkaMessageValue);
    KafkaInboundMessage kafkaInboundMessage =
        KafkaPropertyTransformer.convertConsumerRecordToKafkaInboundMessage(
            consumerRecord, KafkaConnectorConsumer.objectMapper.reader());

    // Then
    assertEquals("my-key", kafkaInboundMessage.getKey());
    assertEquals(kafkaMessageValue, kafkaInboundMessage.getRawValue());
    assertTrue(kafkaInboundMessage.getValue() instanceof JsonNode);
    assertEquals("Item_184", ((JsonNode) kafkaInboundMessage.getValue()).get("itemid").asText());
  }

  public KafkaExecutable getConsumerMock() {
    return new KafkaExecutable(
        properties -> mockConsumer,
        RetryPolicy.builder()
            .handle(Exception.class)
            .withDelay(Duration.ofMillis(50))
            .withMaxAttempts(MAX_ATTEMPTS)
            .build());
  }

  public KafkaExecutable getThrowingConsumerMock() {
    return new KafkaExecutable(
        properties -> {
          throw new RuntimeException("Some Kafka error");
        },
        RetryPolicy.builder()
            .handle(Exception.class)
            .withDelay(Duration.ofMillis(50))
            .withMaxAttempts(INFINITE_RETRIES)
            .build());
  }

  @ParameterizedTest
  @MethodSource("provideStringsForGetOffsets")
  public void testOffsets(Object input, List<Long> expected) {

    var properties = new HashMap<String, Object>();
    properties.put("topic", new KafkaTopic("test", "test"));
    properties.put("offsets", input);
    properties.put("authenticationType", "custom");
    properties.put("autoOffsetReset", "none");

    context =
        InboundConnectorContextBuilder.create()
            .secret("test", "test")
            .properties(properties)
            .definition(InboundConnectorDefinitionBuilder.create().build())
            .validation(new DefaultValidationProvider())
            .build();

    var boundProps = context.bindProperties(KafkaConnectorProperties.class);
    assertThat(boundProps.offsets()).isEqualTo(expected);
  }
}
