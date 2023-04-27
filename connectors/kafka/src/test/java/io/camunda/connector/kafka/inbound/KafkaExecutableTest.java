/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.inbound;

import static io.camunda.connector.kafka.inbound.KafkaExecutable.DEFAULT_KEY_DESERIALIZER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import io.camunda.connector.kafka.outbound.model.KafkaTopic;
import io.camunda.connector.test.inbound.InboundConnectorContextBuilder;
import io.camunda.connector.test.inbound.InboundConnectorPropertiesBuilder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.PartitionInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class KafkaExecutableTest {
  private InboundConnectorContextBuilder.TestInboundConnectorContext context;
  private InboundConnectorContextBuilder.TestInboundConnectorContext originalContext;
  private List<PartitionInfo> topicPartitions;
  private KafkaConnectorProperties kafkaConnectorProperties;
  @Mock private KafkaConsumer<String, String> mockConsumer;

  private String topic;

  @BeforeEach
  public void setUp() {
    topic = "my-topic";
    topicPartitions =
        Arrays.asList(
            new PartitionInfo(topic, 0, null, null, null),
            new PartitionInfo(topic, 1, null, null, null));
    KafkaTopic kafkaTopic = new KafkaTopic();
    kafkaTopic.setTopicName(topic);
    kafkaTopic.setBootstrapServers("localhost:9092");
    kafkaConnectorProperties = new KafkaConnectorProperties();
    kafkaConnectorProperties.setAutoOffsetReset("none");
    kafkaConnectorProperties.setAuthenticationType("custom");
    kafkaConnectorProperties.setTopic(kafkaTopic);
    String jsonString =
        "{'authenticationType':'custom', "
            + "'topic.topicName':'"
            + topic
            + "',"
            + "'topic.bootstrapServers':'localhost:9092',"
            + "'autoOffsetReset':'none'}";
    Gson gson = new Gson();
    Map<String, String> propertiesMap = gson.fromJson(jsonString, Map.class);
    context =
        InboundConnectorContextBuilder.create()
            .secret("test", "test")
            .propertiesAsType(kafkaConnectorProperties)
            .properties(
                InboundConnectorPropertiesBuilder.create()
                    .properties(propertiesMap)
                    .correlationPoint(new ProcessCorrelationPointTest()))
            .build();
    originalContext = context;
  }

  @Test
  public void testActivateMainFunctionality() throws Exception {
    // Given
    when(mockConsumer.partitionsFor(topic)).thenReturn(topicPartitions);
    doNothing().when(mockConsumer).assign(any());
    when(mockConsumer.poll(any())).thenReturn(new ConsumerRecords<>(new HashMap<>()));
    KafkaExecutable kafkaExecutable = getConsumerMock();

    // When
    kafkaExecutable.activate(context);

    // Then
    assertNotNull(kafkaExecutable.consumer);
    assertEquals(mockConsumer, kafkaExecutable.consumer);
    assertEquals(originalContext, context);
    verify(mockConsumer, times(1)).partitionsFor(topic);
    verify(mockConsumer, times(1)).assign(argThat(list -> list.size() == topicPartitions.size()));
    assertNotNull(kafkaExecutable.future);
    kafkaExecutable.shouldLoop = false;
    kafkaExecutable.future.get(3, TimeUnit.SECONDS);
  }

  @Test
  void testActivateAndDeactivate() throws Exception {
    // Given
    when(mockConsumer.partitionsFor(topic)).thenReturn(topicPartitions);
    doNothing().when(mockConsumer).assign(any());
    KafkaExecutable kafkaExecutable = getConsumerMock();

    // When
    kafkaExecutable.activate(context);
    kafkaExecutable.deactivate();

    // Then
    assertEquals(originalContext, context);
    assertNotNull(kafkaExecutable.consumer);
    assertTrue(kafkaExecutable.shouldLoop);
    kafkaExecutable.future.get(3, TimeUnit.SECONDS);
  }

  @Test
  void testGetKafkaProperties() throws Exception {
    // Given
    KafkaExecutable kafkaExecutable = getConsumerMock();

    // When
    Properties properties = kafkaExecutable.getKafkaProperties(kafkaConnectorProperties, context);

    // Then
    assertEquals("localhost:9092", properties.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG));
    assertEquals(
        DEFAULT_KEY_DESERIALIZER, properties.get(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG));
    assertEquals(
        DEFAULT_KEY_DESERIALIZER, properties.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG));
    assertEquals(false, properties.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG));
    assertEquals(
        "kafka-inbound-connector-test-correlation-id",
        properties.get(ConsumerConfig.GROUP_ID_CONFIG));
  }

  @ParameterizedTest
  @MethodSource("provideStringsForGetOffsets")
  public void testGetOffsets(Object input, List<Long> expected) {
    // Given
    KafkaExecutable kafkaExecutable = getConsumerMock();

    // When
    var result = kafkaExecutable.getOffsets(input);

    // Then
    assertEquals(expected, result);
  }

  private static Stream<Arguments> provideStringsForGetOffsets() {
    return Stream.of(
        Arguments.of("10", Arrays.asList(10L)),
        Arguments.of("10,12", Arrays.asList(10L, 12L)),
        Arguments.of(Arrays.asList(10L, 12L), Arrays.asList(10L, 12L)),
        Arguments.of("1,2,3,4,5", Arrays.asList(1L, 2L, 3L, 4L, 5L)));
  }

  @Test
  public void testConvertConsumerRecordToKafkaInboundMessage() {
    // Given
    KafkaExecutable kafkaExecutable = getConsumerMock();

    // When
    KafkaInboundMessage kafkaInboundMessage =
        kafkaExecutable.convertConsumerRecordToKafkaInboundMessage(
            new ConsumerRecord<>("my-topic", 0, 0, "my-key", "{\"foo\": \"bar\"}"));

    // Then
    assertEquals("my-key", kafkaInboundMessage.getKey());
    assertEquals("{\"foo\": \"bar\"}", kafkaInboundMessage.getRawValue());
    Map<String, Object> expectedValue = new HashMap<>();
    expectedValue.put("foo", "bar");
    assertEquals(expectedValue, kafkaInboundMessage.getValue());
  }

  public KafkaExecutable getConsumerMock() {
    return new KafkaExecutable(properties -> mockConsumer);
  }
}
