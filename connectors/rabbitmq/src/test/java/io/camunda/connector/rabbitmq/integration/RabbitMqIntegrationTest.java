/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import io.camunda.connector.api.inbound.InboundConnectorDefinition;
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
import io.camunda.connector.rabbitmq.supplier.ConnectionFactorySupplier;
import io.camunda.connector.rabbitmq.supplier.ObjectMapperSupplier;
import io.camunda.connector.runtime.test.inbound.InboundConnectorContextBuilder;
import io.camunda.connector.runtime.test.inbound.InboundConnectorContextBuilder.TestInboundConnectorContext;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;

@SlowTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RabbitMqIntegrationTest extends BaseTest {

  @Container
  @SuppressWarnings("resource")
  private static final RabbitMQContainer rabbitMq =
      new RabbitMQContainer(BaseTest.RABBITMQ_TEST_IMAGE)
          .withUser(Authentication.USERNAME, Authentication.PASSWORD, Set.of("administrator"))
          .withVhost(Routing.VIRTUAL_HOST)
          .withPermission(Routing.VIRTUAL_HOST, Authentication.USERNAME, ".*", ".*", ".*")
          .withQueue(Routing.VIRTUAL_HOST, ActualValue.QUEUE_NAME)
          .withQueue(Routing.VIRTUAL_HOST, ActualValue.QUEUE_NAME + "_TO_DELETE")
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
    OutboundConnectorFunction function = new RabbitMqFunction();

    FactoryRoutingData routingData =
        new FactoryRoutingData(
            Routing.VIRTUAL_HOST, rabbitMq.getHost(), rabbitMq.getAmqpPort().toString());

    RabbitMqOutboundRouting routing =
        new RabbitMqOutboundRouting(
            Routing.EXCHANGE,
            Routing.ROUTING_KEY,
            Routing.VIRTUAL_HOST,
            rabbitMq.getHost(),
            rabbitMq.getAmqpPort().toString());

    RabbitMqMessage message = new RabbitMqMessage(null, "{\"value\": \"Hello World\"}");

    RabbitMqRequest request = new RabbitMqRequest("sendMessage", getAuth(), routing, message);

    var json = ObjectMapperSupplier.instance().writeValueAsString(request);
    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .validation(new DefaultValidationProvider())
            .variables(json)
            .build();

    var result = function.execute(context);

    assertInstanceOf(RabbitMqResult.class, result);
    RabbitMqResult castedResult = (RabbitMqResult) result;
    assertEquals("success", castedResult.getStatusResult());
  }

  @Test
  @Order(2)
  void consumeMessageWithInboundConnector() throws Exception {
    RabbitMqExecutable executable = new RabbitMqExecutable(new ConnectionFactorySupplier());

    RabbitMqInboundProperties properties = new RabbitMqInboundProperties();
    properties.setAuthentication(getAuth());
    properties.setQueueName(ActualValue.QUEUE_NAME);

    FactoryRoutingData routingData =
        new FactoryRoutingData(
            Routing.VIRTUAL_HOST, rabbitMq.getHost(), rabbitMq.getAmqpPort().toString());
    properties.setRouting(routingData);

    TestInboundConnectorContext context =
        InboundConnectorContextBuilder.create()
            .definition(new InboundConnectorDefinition(null, null, null, List.of()))
            .properties(properties)
            .build();

    executable.activate(context);
    await().atMost(Duration.ofSeconds(5)).until(() -> context.getCorrelations().size() > 0);
    executable.deactivate();

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
    OutboundConnectorFunction function = new RabbitMqFunction();

    FactoryRoutingData routingData =
        new FactoryRoutingData(
            Routing.VIRTUAL_HOST, rabbitMq.getHost(), rabbitMq.getAmqpPort().toString());

    RabbitMqOutboundRouting routing =
        new RabbitMqOutboundRouting(
            Routing.EXCHANGE,
            Routing.ROUTING_KEY,
            Routing.VIRTUAL_HOST,
            rabbitMq.getHost(),
            rabbitMq.getAmqpPort().toString());

    RabbitMqMessage messageOutbound =
        new RabbitMqMessage(null, "{\"value\": \"Hello “ ” \\\"World\\\"\"}");

    RabbitMqRequest request = new RabbitMqRequest(null, getAuth(), routing, messageOutbound);

    var json = ObjectMapperSupplier.instance().writeValueAsString(request);
    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .validation(new DefaultValidationProvider())
            .variables(json)
            .build();

    var result = function.execute(context);

    assertInstanceOf(RabbitMqResult.class, result);
    RabbitMqResult castedResult = (RabbitMqResult) result;
    assertEquals("success", castedResult.getStatusResult());

    RabbitMqExecutable executable = new RabbitMqExecutable(new ConnectionFactorySupplier());

    RabbitMqInboundProperties properties = new RabbitMqInboundProperties();
    properties.setAuthentication(getAuth());
    properties.setQueueName(ActualValue.QUEUE_NAME);

    FactoryRoutingData routingDataInbound =
        new FactoryRoutingData(
            Routing.VIRTUAL_HOST, rabbitMq.getHost(), rabbitMq.getAmqpPort().toString());
    properties.setRouting(routingDataInbound);

    TestInboundConnectorContext contextInbound =
        InboundConnectorContextBuilder.create()
            .definition(new InboundConnectorDefinition(null, null, null, List.of()))
            .properties(properties)
            .build();

    executable.activate(contextInbound);
    await().atMost(Duration.ofSeconds(5)).until(() -> !contextInbound.getCorrelations().isEmpty());
    executable.deactivate();

    assertEquals(1, contextInbound.getCorrelations().size());
    assertInstanceOf(RabbitMqInboundResult.class, contextInbound.getCorrelations().get(0));
    RabbitMqInboundResult castedResultInbound =
        (RabbitMqInboundResult) contextInbound.getCorrelations().get(0);
    RabbitMqInboundMessage messageInbound = castedResultInbound.message();
    assertInstanceOf(Map.class, messageInbound.body());
    Map<String, Object> body = (Map<String, Object>) messageInbound.body();
    assertEquals("Hello “ ” \"World\"", body.get("value"));
  }

  @Test
  @Order(4)
  void deleteQueueWithOutboundConnector() throws Exception {
    OutboundConnectorFunction function = new RabbitMqFunction();
    final String queueToDelete = ActualValue.QUEUE_NAME + "_TO_DELETE";

    RabbitMqRequest request = new RabbitMqRequest("deleteQueue", getAuth(), null, null);

    var json = ObjectMapperSupplier.instance().writeValueAsString(request);
    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .validation(new DefaultValidationProvider())
            .variables(json)
            .build();

    function.execute(context);

    ConnectionFactory factory = new ConnectionFactory();
    factory.setUsername(Authentication.USERNAME);
    factory.setPassword(Authentication.PASSWORD);
    factory.setVirtualHost(Routing.VIRTUAL_HOST);
    factory.setHost(rabbitMq.getHost());
    factory.setPort(rabbitMq.getAmqpPort());

    try (com.rabbitmq.client.Connection conn = factory.newConnection();
        Channel channel = conn.createChannel()) {

      channel.queueDeclarePassive(queueToDelete);
      fail("Queue should have been deleted but was found.");

    } catch (IOException e) {
      String errorMessage = e.getMessage();
      if (errorMessage != null) {
        assertTrue(errorMessage.contains("404"), "Expected 404, but got: " + errorMessage);
      } else {
        return;
      }
    }
  }

  private RabbitMqAuthentication getAuth() {
    return new CredentialsAuthentication(Authentication.USERNAME, Authentication.PASSWORD);
  }
}
