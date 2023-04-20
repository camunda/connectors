/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.impl.ConnectorInputException;
import io.camunda.connector.impl.inbound.result.MessageCorrelationResult;
import io.camunda.connector.rabbitmq.inbound.model.RabbitMqInboundResult;
import io.camunda.connector.rabbitmq.inbound.model.RabbitMqInboundResult.RabbitMqInboundMessage;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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
    InboundConnectorContext ctx = getContextBuilderWithSecrets()
        .result(new MessageCorrelationResult("", 0))
        .build();
    spyContext = spy(ctx);

    consumer = new RabbitMqConsumer(mockChannel, spyContext);
  }

  @Nested
  class SuccessCases {

    @Test
    void consumer_shouldDeserializeJsonPayload() throws IOException {
      // Given JSON payload
      ArgumentCaptor<RabbitMqInboundResult> captor =
          ArgumentCaptor.forClass(RabbitMqInboundResult.class);

      Envelope envelope = new Envelope(1, false, "exchange", "routingKey");
      BasicProperties properties = new BasicProperties.Builder().build();
      String jsonBody = "{\"key\":\"value\"}";

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

    @Test
    void consumer_shouldHandlePlaintextPayload() throws IOException {
      // Given plaintext payload
      ArgumentCaptor<RabbitMqInboundResult> captor =
          ArgumentCaptor.forClass(RabbitMqInboundResult.class);

      Envelope envelope = new Envelope(1, false, "exchange", "routingKey");
      BasicProperties properties = new BasicProperties.Builder().build();
      String body = "plaintext";

      // When
      consumer.handleDelivery("consumerTag", envelope, properties, body.getBytes());

      // Then
      verify(spyContext, times(1)).correlate(captor.capture());
      RabbitMqInboundMessage message = captor.getValue().getMessage();

      assertThat(message.getBody()).isInstanceOf(String.class);
      assertThat(message.getBody()).isEqualTo(body);

      assertThat(message.getProperties()).isEqualTo(properties);
      assertThat(message.getConsumerTag()).isEqualTo("consumerTag");

      verify(mockChannel, times(1)).basicAck(1, false);
    }

    @Test
    void consumer_shouldHandleNumericPayload() throws IOException {
      // Given plaintext payload
      ArgumentCaptor<RabbitMqInboundResult> captor =
          ArgumentCaptor.forClass(RabbitMqInboundResult.class);

      Envelope envelope = new Envelope(1, false, "exchange", "routingKey");
      BasicProperties properties = new BasicProperties.Builder().build();
      String body = "3";

      // When
      consumer.handleDelivery("consumerTag", envelope, properties, body.getBytes());

      // Then
      verify(spyContext, times(1)).correlate(captor.capture());
      RabbitMqInboundMessage message = captor.getValue().getMessage();

      assertThat(message.getBody()).isInstanceOf(Number.class);
      assertThat(((Number) message.getBody()).intValue()).isEqualTo(Integer.parseInt(body));

      assertThat(message.getProperties()).isEqualTo(properties);
      assertThat(message.getConsumerTag()).isEqualTo("consumerTag");

      verify(mockChannel, times(1)).basicAck(1, false);
    }

    @Test
    void consumer_shouldHandleBooleanPayload() throws IOException {
      // Given plaintext payload
      ArgumentCaptor<RabbitMqInboundResult> captor =
          ArgumentCaptor.forClass(RabbitMqInboundResult.class);

      Envelope envelope = new Envelope(1, false, "exchange", "routingKey");
      BasicProperties properties = new BasicProperties.Builder().build();
      String body = "true";

      // When
      consumer.handleDelivery("consumerTag", envelope, properties, body.getBytes());

      // Then
      verify(spyContext, times(1)).correlate(captor.capture());
      RabbitMqInboundMessage message = captor.getValue().getMessage();

      assertThat(message.getBody()).isInstanceOf(Boolean.class);
      assertThat(message.getBody()).isEqualTo(Boolean.parseBoolean(body));

      assertThat(message.getProperties()).isEqualTo(properties);
      assertThat(message.getConsumerTag()).isEqualTo("consumerTag");

      verify(mockChannel, times(1)).basicAck(1, false);
    }
  }

  @Test
  void consumer_shouldNackAndRequeue_UnexpectedError() throws IOException {
    // Given that correlation throws random exception
    when(spyContext.correlate(any())).thenThrow(new RuntimeException("Meh, Zeebe is broken"));

    ArgumentCaptor<RabbitMqInboundResult> captor =
        ArgumentCaptor.forClass(RabbitMqInboundResult.class);

    Envelope envelope = new Envelope(1, false, "exchange", "routingKey");
    BasicProperties properties = new BasicProperties.Builder().build();
    String body = "plaintext";

    // When
    consumer.handleDelivery("consumerTag", envelope, properties, body.getBytes());

    // Then
    verify(spyContext, times(1)).correlate(captor.capture());
    RabbitMqInboundMessage message = captor.getValue().getMessage();

    assertThat(message.getBody()).isInstanceOf(String.class);
    assertThat(message.getBody()).isEqualTo(body);

    assertThat(message.getProperties()).isEqualTo(properties);
    assertThat(message.getConsumerTag()).isEqualTo("consumerTag");

    verify(mockChannel, times(1)).basicReject(1, true);
  }

  @Test
  void consumer_shouldNackAndNoRequeue_ConnectorInputException() throws IOException {
    // Given that correlation error is wrapped into ConnectorInputException
    when(spyContext.correlate(any())).thenThrow(
        new ConnectorInputException(new RuntimeException("Payload is invalid")));

    ArgumentCaptor<RabbitMqInboundResult> captor =
        ArgumentCaptor.forClass(RabbitMqInboundResult.class);

    Envelope envelope = new Envelope(1, false, "exchange", "routingKey");
    BasicProperties properties = new BasicProperties.Builder().build();
    String body = "plaintext";

    // When
    consumer.handleDelivery("consumerTag", envelope, properties, body.getBytes());

    // Then
    verify(spyContext, times(1)).correlate(captor.capture());
    RabbitMqInboundMessage message = captor.getValue().getMessage();

    assertThat(message.getBody()).isInstanceOf(String.class);
    assertThat(message.getBody()).isEqualTo(body);

    assertThat(message.getProperties()).isEqualTo(properties);
    assertThat(message.getConsumerTag()).isEqualTo("consumerTag");

    verify(mockChannel, times(1)).basicReject(1, false);
  }

  @Test
  void consumer_shouldHandleCancel() {
    // Given
    String consumerTag = "consumerTag";

    // When
    consumer.handleCancel(consumerTag);

    // Then
    verify(spyContext, times(1)).cancel(null);
  }

  @Test
  void consumer_shouldHandleShutdown() {
    // Given
    String consumerTag = "consumerTag";
    ShutdownSignalException cause = new ShutdownSignalException(true, false, null, null);

    // When
    consumer.handleShutdownSignal(consumerTag, cause);

    // Then
    verify(spyContext, times(1)).cancel(cause);
  }
}
