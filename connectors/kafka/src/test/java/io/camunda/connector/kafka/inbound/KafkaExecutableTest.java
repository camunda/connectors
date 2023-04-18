/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.inbound;

import static io.camunda.connector.kafka.inbound.KafkaExecutable.DEFAULT_GROUP_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.kafka.outbound.model.KafkaTopic;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class KafkaExecutableTest {

  @Mock private InboundConnectorContext context;

  private KafkaExecutable kafkaExecutable;

  @BeforeEach
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    kafkaExecutable = new KafkaExecutable();
  }

  @Test
  public void testActivate() throws Exception {
    String topic = "my-topic";
    KafkaConnectorProperties props = new KafkaConnectorProperties();
    KafkaTopic kafkaTopic = new KafkaTopic();
    kafkaTopic.setBootstrapServers("localhost:9092");
    kafkaTopic.setTopicName(topic);
    props.setTopic(kafkaTopic);
    when(context.getPropertiesAsType(any())).thenReturn(props);
    doNothing().when(context).replaceSecrets(any());
    doNothing().when(context).validate(any());

    kafkaExecutable.activate(context);

    // Verify that the Kafka consumer was created and subscribed to the correct topic
    assertNotNull(kafkaExecutable.future);
    kafkaExecutable.shouldLoop = false;
    kafkaExecutable.future.get(3, TimeUnit.SECONDS);
    assertNotNull(kafkaExecutable.consumer);
    assertEquals(1, kafkaExecutable.consumer.subscription().size());
    assertEquals(topic, kafkaExecutable.consumer.subscription().iterator().next());
    assertEquals(DEFAULT_GROUP_ID, kafkaExecutable.consumer.groupMetadata().groupId());
  }

  @Test
  public void testConvertConsumerRecordToKafkaInboundMessage() {
    KafkaInboundMessage kafkaInboundMessage =
        kafkaExecutable.convertConsumerRecordToKafkaInboundMessage(
            new ConsumerRecord<>("my-topic", 0, 0, "my-key", "{\"foo\": \"bar\"}"));

    assertEquals("my-key", kafkaInboundMessage.getKey());
    assertEquals("{\"foo\": \"bar\"}", kafkaInboundMessage.getRawValue());
    Map<String, Object> expectedValue = new HashMap<>();
    expectedValue.put("foo", "bar");
    assertEquals(expectedValue, kafkaInboundMessage.getValue());
  }

  // Add more tests as needed
}
