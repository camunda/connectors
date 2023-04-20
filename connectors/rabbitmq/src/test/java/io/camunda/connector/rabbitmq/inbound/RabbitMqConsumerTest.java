/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.impl.inbound.result.MessageCorrelationResult;
import io.camunda.connector.rabbitmq.inbound.model.RabbitMqInboundResult;
import io.camunda.connector.rabbitmq.inbound.model.RabbitMqInboundResult.RabbitMqInboundMessage;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RabbitMqConsumerTest extends InboundBaseTest {

  Channel mockChannel;
  InboundConnectorContext spyContext;
  RabbitMqConsumer consumer;

  @BeforeEach
  void initConsumer() {
    mockChannel = mock(Channel.class);
    spyContext =
        spy(getContextBuilderWithSecrets().result(new MessageCorrelationResult("", 0)).build());

    consumer = new RabbitMqConsumer(mockChannel, spyContext);
  }

  @Test
  void consumer_shouldHandleValidMessages() throws IOException {
    // Given
    ArgumentCaptor<RabbitMqInboundResult> captor =
        ArgumentCaptor.forClass(RabbitMqInboundResult.class);

    Envelope envelope = new Envelope(1, false, "exchange", "routingKey");
    BasicProperties properties = new BasicProperties.Builder().build();
    String jsonBody = "{\"key\":\"value\"}";

    System.out.println(spyContext.correlate(null));

    // When
    consumer.handleDelivery("consumerTag", envelope, properties, jsonBody.getBytes());

    // Then
    verify(spyContext, times(1)).correlate(captor.capture());
    RabbitMqInboundMessage message = captor.getValue().getMessage();

    assertThat(message.getBody()).isInstanceOf(Map.class);
    Map<String, Object> body = (Map<String, Object>) message.getBody();
    assertThat(body).containsEntry("key", "value");

    assertThat(message.getProperties()).isEqualTo(properties);
    assertThat(message.getConsumerTag()).isEqualTo("consumerTag");

    verify(mockChannel, times(1)).basicAck(1, false);
  }
}
