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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.impl.LongStringHelper;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.impl.ConnectorInputException;
import io.camunda.connector.impl.inbound.result.MessageCorrelationResult;
import io.camunda.connector.rabbitmq.inbound.model.RabbitMqInboundResult;
import io.camunda.connector.rabbitmq.inbound.model.RabbitMqInboundResult.RabbitMqInboundMessage;
import io.camunda.connector.rabbitmq.inbound.model.RabbitMqMessageProperties;
import io.camunda.connector.test.inbound.InboundConnectorContextBuilder.TestInboundConnectorContext;
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

  @BeforeEach
  void init() {
    mockChannel = mock(Channel.class);
  }

  @Nested
  class SuccessCases {

    TestInboundConnectorContext context;
    RabbitMqConsumer consumer;

    @BeforeEach
    void init() {
      context = getContextBuilderWithSecrets().result(new MessageCorrelationResult("", 0)).build();
      consumer = new RabbitMqConsumer(mockChannel, context);
    }

    @Test
    void consumer_shouldDeserializeJsonPayload() throws IOException {
      // Given JSON payload
      Envelope envelope = new Envelope(1, false, "exchange", "routingKey");
      BasicProperties properties = new BasicProperties.Builder().build();
      String jsonBody = "{\"key\":\"value\"}";

      // When
      consumer.handleDelivery("consumerTag", envelope, properties, jsonBody.getBytes());

      // Then
      var correlatedEvents = context.getCorrelations();
      assertThat(correlatedEvents).hasSize(1);
      assertThat(correlatedEvents.get(0)).isInstanceOf(RabbitMqInboundResult.class);
      RabbitMqInboundMessage message =
          ((RabbitMqInboundResult) correlatedEvents.get(0)).message();

      assertThat(message.body()).isInstanceOf(Map.class);
      Map<String, Object> body = (Map<String, Object>) message.body();
      assertThat(body).containsEntry("key", "value");

      assertThat(message.properties()).isEqualTo(new RabbitMqMessageProperties(properties));
      assertThat(message.consumerTag()).isEqualTo("consumerTag");

      verify(mockChannel, times(1)).basicAck(1, false);
    }

    @Test
    void consumer_shouldHandlePlaintextPayload() throws IOException {
      // Given plaintext payload
      Envelope envelope = new Envelope(1, false, "exchange", "routingKey");
      BasicProperties properties = new BasicProperties.Builder().build();
      String body = "plaintext";

      // When
      consumer.handleDelivery("consumerTag", envelope, properties, body.getBytes());

      // Then
      var correlatedEvents = context.getCorrelations();
      assertThat(correlatedEvents).hasSize(1);
      assertThat(correlatedEvents.get(0)).isInstanceOf(RabbitMqInboundResult.class);
      RabbitMqInboundMessage message =
          ((RabbitMqInboundResult) correlatedEvents.get(0)).message();

      assertThat(message.body()).isInstanceOf(String.class);
      assertThat(message.body()).isEqualTo(body);

      assertThat(message.properties()).isEqualTo(new RabbitMqMessageProperties(properties));
      assertThat(message.consumerTag()).isEqualTo("consumerTag");

      verify(mockChannel, times(1)).basicAck(1, false);
    }

    @Test
    void consumer_shouldHandleNumericPayload() throws IOException {
      // Given plaintext payload
      Envelope envelope = new Envelope(1, false, "exchange", "routingKey");
      BasicProperties properties = new BasicProperties.Builder().build();
      String body = "3";

      // When
      consumer.handleDelivery("consumerTag", envelope, properties, body.getBytes());

      // Then
      var correlatedEvents = context.getCorrelations();
      assertThat(correlatedEvents).hasSize(1);
      assertThat(correlatedEvents.get(0)).isInstanceOf(RabbitMqInboundResult.class);
      RabbitMqInboundMessage message =
          ((RabbitMqInboundResult) correlatedEvents.get(0)).message();

      assertThat(message.body()).isInstanceOf(Number.class);
      assertThat(((Number) message.body()).intValue()).isEqualTo(Integer.parseInt(body));

      assertThat(message.properties()).isEqualTo(new RabbitMqMessageProperties(properties));
      assertThat(message.consumerTag()).isEqualTo("consumerTag");

      verify(mockChannel, times(1)).basicAck(1, false);
    }

    @Test
    void consumer_shouldHandleBooleanPayload() throws IOException {
      // Given plaintext payload
      Envelope envelope = new Envelope(1, false, "exchange", "routingKey");
      BasicProperties properties = new BasicProperties.Builder().build();
      String body = "true";

      // When
      consumer.handleDelivery("consumerTag", envelope, properties, body.getBytes());

      // Then
      var correlatedEvents = context.getCorrelations();
      assertThat(correlatedEvents).hasSize(1);
      assertThat(correlatedEvents.get(0)).isInstanceOf(RabbitMqInboundResult.class);
      RabbitMqInboundMessage message =
          ((RabbitMqInboundResult) correlatedEvents.get(0)).message();

      assertThat(message.body()).isInstanceOf(Boolean.class);
      assertThat(message.body()).isEqualTo(Boolean.parseBoolean(body));

      assertThat(message.properties()).isEqualTo(new RabbitMqMessageProperties(properties));
      assertThat(message.consumerTag()).isEqualTo("consumerTag");

      verify(mockChannel, times(1)).basicAck(1, false);
    }

    @Test
    void consumer_shouldHandleByteArrayHeaders() throws IOException {
      // Given headers provided as bytes
      Envelope envelope = new Envelope(1, false, "exchange", "routingKey");
      BasicProperties properties = new BasicProperties.Builder()
          .headers(Map.of("key", LongStringHelper.asLongString("value"))).build();

      // When
      consumer.handleDelivery("consumerTag", envelope, properties, "body".getBytes());

      // Then
      var correlatedEvents = context.getCorrelations();
      assertThat(correlatedEvents).hasSize(1);
      assertThat(correlatedEvents.get(0)).isInstanceOf(RabbitMqInboundResult.class);
      RabbitMqInboundMessage message =
          ((RabbitMqInboundResult) correlatedEvents.get(0)).message();

      assertThat(message.properties().headers().get("key")).isEqualTo("value");
    }
  }

  @Test
  void consumer_shouldNackAndRequeue_UnexpectedError() throws IOException {
    // Given that correlation throws random exception
    var mockContext = mock(InboundConnectorContext.class);
    when(mockContext.correlate(any())).thenThrow(new RuntimeException("Meh, Zeebe is broken"));
    var consumer = new RabbitMqConsumer(mockChannel, mockContext);

    ArgumentCaptor<RabbitMqInboundResult> captor =
        ArgumentCaptor.forClass(RabbitMqInboundResult.class);

    Envelope envelope = new Envelope(1, false, "exchange", "routingKey");
    BasicProperties properties = new BasicProperties.Builder().build();
    String body = "plaintext";

    // When
    consumer.handleDelivery("consumerTag", envelope, properties, body.getBytes());

    // Then
    verify(mockContext, times(1)).correlate(captor.capture());
    RabbitMqInboundMessage message = captor.getValue().message();

    assertThat(message.body()).isInstanceOf(String.class);
    assertThat(message.body()).isEqualTo(body);

    assertThat(message.properties()).isEqualTo(new RabbitMqMessageProperties(properties));
    assertThat(message.consumerTag()).isEqualTo("consumerTag");

    verify(mockChannel, times(1)).basicReject(1, true);
  }

  @Test
  void consumer_shouldNackAndNoRequeue_ConnectorInputException() throws IOException {
    // Given that correlation error is wrapped into ConnectorInputException
    var mockContext = mock(InboundConnectorContext.class);
    when(mockContext.correlate(any()))
        .thenThrow(new ConnectorInputException(new RuntimeException("Payload is invalid")));
    var consumer = new RabbitMqConsumer(mockChannel, mockContext);

    ArgumentCaptor<RabbitMqInboundResult> captor =
        ArgumentCaptor.forClass(RabbitMqInboundResult.class);

    Envelope envelope = new Envelope(1, false, "exchange", "routingKey");
    BasicProperties properties = new BasicProperties.Builder().build();
    String body = "plaintext";

    // When
    consumer.handleDelivery("consumerTag", envelope, properties, body.getBytes());

    // Then
    verify(mockContext, times(1)).correlate(captor.capture());
    RabbitMqInboundMessage message = captor.getValue().message();

    assertThat(message.body()).isInstanceOf(String.class);
    assertThat(message.body()).isEqualTo(body);

    assertThat(message.properties()).isEqualTo(new RabbitMqMessageProperties(properties));
    assertThat(message.consumerTag()).isEqualTo("consumerTag");

    verify(mockChannel, times(1)).basicReject(1, false);
  }

  @Test
  void consumer_shouldHandleCancel() {
    // Given
    String consumerTag = "consumerTag";
    var spyContext = mock(InboundConnectorContext.class);
    var consumer = new RabbitMqConsumer(mockChannel, spyContext);

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
    var spyContext = mock(InboundConnectorContext.class);
    var consumer = new RabbitMqConsumer(mockChannel, spyContext);

    // When
    consumer.handleShutdownSignal(consumerTag, cause);

    // Then
    verify(spyContext, times(1)).cancel(cause);
  }
}
