/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.rabbitmq.BaseTest;
import io.camunda.connector.rabbitmq.BaseTest.ActualValue.Authentication;
import io.camunda.connector.rabbitmq.BaseTest.ActualValue.Routing;
import io.camunda.connector.rabbitmq.common.model.CredentialsAuthentication;
import io.camunda.connector.rabbitmq.common.model.FactoryRoutingData;
import io.camunda.connector.rabbitmq.common.model.RabbitMqAuthentication;
import io.camunda.connector.rabbitmq.inbound.RabbitMqExecutable;
import io.camunda.connector.rabbitmq.inbound.model.RabbitMqInboundProperties;
import io.camunda.connector.rabbitmq.inbound.model.RabbitMqInboundResult;
import io.camunda.connector.rabbitmq.inbound.model.RabbitMqInboundResult.RabbitMqInboundMessage;
import io.camunda.connector.rabbitmq.outbound.RabbitMqFunction;
import io.camunda.connector.rabbitmq.outbound.RabbitMqResult;
import io.camunda.connector.rabbitmq.outbound.model.RabbitMqMessage;
import io.camunda.connector.rabbitmq.outbound.model.RabbitMqOutboundRouting;
import io.camunda.connector.rabbitmq.outbound.model.RabbitMqRequest;
import io.camunda.connector.rabbitmq.supplier.ObjectMapperSupplier;
import io.camunda.connector.test.inbound.InboundConnectorContextBuilder;
import io.camunda.connector.test.inbound.InboundConnectorContextBuilder.TestInboundConnectorContext;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;

@Disabled // to be run manually
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RabbitMqIntegrationTest extends BaseTest {

  @Container
  private static final RabbitMQContainer rabbitMq =
      new RabbitMQContainer("rabbitmq:3.7.25-management-alpine")
          .withUser(Authentication.USERNAME, Authentication.PASSWORD, Set.of("administrator"))
          .withVhost(Routing.VIRTUAL_HOST)
          .withPermission(Routing.VIRTUAL_HOST, Authentication.USERNAME, ".*", ".*", ".*")
          .withQueue(Routing.VIRTUAL_HOST, ActualValue.QUEUE_NAME)
          .withExchange(Routing.VIRTUAL_HOST, Routing.EXCHANGE, "direct")
          .withBinding(
              Routing.VIRTUAL_HOST,
              Routing.EXCHANGE,
              ActualValue.QUEUE_NAME,
              Map.of(),
              Routing.ROUTING_KEY,
              "queue");

  @BeforeAll
  public static void init() {
    rabbitMq.start();
  }

  @Test
  @Order(1)
  void publishMessageWithOutboundConnector() throws Exception {
    // Given
    OutboundConnectorFunction function = new RabbitMqFunction();

    RabbitMqOutboundRouting routing =
        new RabbitMqOutboundRouting(
            Routing.EXCHANGE,
            Routing.ROUTING_KEY,
            Routing.VIRTUAL_HOST,
            rabbitMq.getHost(),
            rabbitMq.getAmqpPort().toString());

    RabbitMqMessage message = new RabbitMqMessage(null, "{\"value\": \"Hello World\"}");

    RabbitMqRequest request = new RabbitMqRequest(getAuth(), routing, message);

    var json = ObjectMapperSupplier.instance().writeValueAsString(request);
    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .validation(new DefaultValidationProvider())
            .variables(json)
            .build();

    // When
    var result = function.execute(context);

    // Then
    assertInstanceOf(RabbitMqResult.class, result);
    RabbitMqResult castedResult = (RabbitMqResult) result;
    assertEquals("success", castedResult.getStatusResult());
  }

  @Test
  @Order(2)
  void consumeMessageWithInboundConnector() throws Exception {
    // Given
    RabbitMqExecutable executable = new RabbitMqExecutable();

    RabbitMqInboundProperties properties = new RabbitMqInboundProperties();
    properties.setAuthentication(getAuth());
    properties.setQueueName(ActualValue.QUEUE_NAME);

    FactoryRoutingData routingData =
        new FactoryRoutingData(
            Routing.VIRTUAL_HOST, rabbitMq.getHost(), rabbitMq.getAmqpPort().toString());
    properties.setRouting(routingData);

    TestInboundConnectorContext context =
        InboundConnectorContextBuilder.create().properties(properties).build();

    // When
    executable.activate(context);
    await().atMost(Duration.ofSeconds(5)).until(() -> context.getCorrelations().size() > 0);
    executable.deactivate();

    // Then
    assertEquals(1, context.getCorrelations().size());
    assertInstanceOf(RabbitMqInboundResult.class, context.getCorrelations().get(0));
    RabbitMqInboundResult castedResult = (RabbitMqInboundResult) context.getCorrelations().get(0);
    RabbitMqInboundMessage message = castedResult.message();
    assertInstanceOf(Map.class, message.body());
    Map<String, Object> body = (Map<String, Object>) message.body();
    assertEquals("Hello World", body.get("value"));
  }

  @Test
  @Order(3)
  void publishMessageWithOutboundConnectorAndConsumeMessageWithInboundConnector() throws Exception {
    // Given
    OutboundConnectorFunction function = new RabbitMqFunction();

    RabbitMqOutboundRouting routing =
        new RabbitMqOutboundRouting(
            Routing.EXCHANGE,
            Routing.ROUTING_KEY,
            Routing.VIRTUAL_HOST,
            rabbitMq.getHost(),
            rabbitMq.getAmqpPort().toString());

    // '“' and '”' are special unicode char.
    RabbitMqMessage messageOutbound =
        new RabbitMqMessage(null, "{\"value\": \"Hello “ ” \\\"World\\\"\"}");

    RabbitMqRequest request = new RabbitMqRequest(getAuth(), routing, messageOutbound);

    var json = ObjectMapperSupplier.instance().writeValueAsString(request);
    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .validation(new DefaultValidationProvider())
            .variables(json)
            .build();

    // When
    var result = function.execute(context);

    // Then
    assertInstanceOf(RabbitMqResult.class, result);
    RabbitMqResult castedResult = (RabbitMqResult) result;
    assertEquals("success", castedResult.getStatusResult());

    // Given
    RabbitMqExecutable executable = new RabbitMqExecutable();

    RabbitMqInboundProperties properties = new RabbitMqInboundProperties();
    properties.setAuthentication(getAuth());
    properties.setQueueName(ActualValue.QUEUE_NAME);

    FactoryRoutingData routingData =
        new FactoryRoutingData(
            Routing.VIRTUAL_HOST, rabbitMq.getHost(), rabbitMq.getAmqpPort().toString());
    properties.setRouting(routingData);

    TestInboundConnectorContext contextInbound =
        InboundConnectorContextBuilder.create().properties(properties).build();

    // When
    executable.activate(contextInbound);
    await().atMost(Duration.ofSeconds(5)).until(() -> !contextInbound.getCorrelations().isEmpty());
    executable.deactivate();

    // Then
    assertEquals(1, contextInbound.getCorrelations().size());
    assertInstanceOf(RabbitMqInboundResult.class, contextInbound.getCorrelations().get(0));
    RabbitMqInboundResult castedResultInbound =
        (RabbitMqInboundResult) contextInbound.getCorrelations().get(0);
    RabbitMqInboundMessage messageInbound = castedResultInbound.message();
    assertInstanceOf(Map.class, messageInbound.body());
    Map<String, Object> body = (Map<String, Object>) messageInbound.body();
    assertEquals("Hello “ ” \"World\"", body.get("value"));
  }

  private RabbitMqAuthentication getAuth() {
    return new CredentialsAuthentication(Authentication.USERNAME, Authentication.PASSWORD);
  }
}
