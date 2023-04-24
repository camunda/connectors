package io.camunda.connector.rabbitmq.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.impl.inbound.result.MessageCorrelationResult;
import io.camunda.connector.rabbitmq.BaseTest;
import io.camunda.connector.rabbitmq.BaseTest.ActualValue.Authentication;
import io.camunda.connector.rabbitmq.BaseTest.ActualValue.Routing;
import io.camunda.connector.rabbitmq.common.model.RabbitMqAuthentication;
import io.camunda.connector.rabbitmq.common.model.RabbitMqAuthenticationType;
import io.camunda.connector.rabbitmq.common.model.RabbitMqMessage;
import io.camunda.connector.rabbitmq.common.model.RabbitMqRouting;
import io.camunda.connector.rabbitmq.inbound.RabbitMqExecutable;
import io.camunda.connector.rabbitmq.inbound.model.RabbitMqInboundProperties;
import io.camunda.connector.rabbitmq.inbound.model.RabbitMqInboundResult;
import io.camunda.connector.rabbitmq.inbound.model.RabbitMqInboundResult.RabbitMqInboundMessage;
import io.camunda.connector.rabbitmq.outbound.RabbitMqFunction;
import io.camunda.connector.rabbitmq.outbound.RabbitMqResult;
import io.camunda.connector.rabbitmq.outbound.model.RabbitMqOutboundRouting;
import io.camunda.connector.rabbitmq.outbound.model.RabbitMqRequest;
import io.camunda.connector.test.inbound.InboundConnectorContextBuilder;
import io.camunda.connector.test.inbound.InboundConnectorContextBuilder.TestInboundConnectorContext;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
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
          .withBinding(Routing.VIRTUAL_HOST, Routing.EXCHANGE, ActualValue.QUEUE_NAME, Map.of(), Routing.ROUTING_KEY, "queue");

  @BeforeAll
  public static void init() {
    rabbitMq.start();
  }

  @Test
  @Order(1)
  void publishMessageWithOutboundConnector() throws Exception {
    // Given
    OutboundConnectorFunction function = new RabbitMqFunction();

    RabbitMqRequest request = new RabbitMqRequest();
    request.setAuthentication(getAuth());
    RabbitMqOutboundRouting routing = new RabbitMqOutboundRouting();
    routing.setRoutingKey(Routing.ROUTING_KEY);
    routing.setExchange(Routing.EXCHANGE);
    routing.setVirtualHost(Routing.VIRTUAL_HOST);
    routing.setHostName(rabbitMq.getHost());
    routing.setPort(rabbitMq.getAmqpPort().toString());
    request.setRouting(routing);
    RabbitMqMessage message = new RabbitMqMessage();
    message.setBody("{\"value\": \"Hello World\"}");
    request.setMessage(message);

    OutboundConnectorContext context = OutboundConnectorContextBuilder.create()
        .variables(request)
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
    RabbitMqRouting routing = new RabbitMqRouting();
    routing.setHostName(rabbitMq.getHost());
    routing.setPort(rabbitMq.getAmqpPort().toString());
    routing.setVirtualHost(Routing.VIRTUAL_HOST);
    properties.setRouting(routing);

    TestInboundConnectorContext context = InboundConnectorContextBuilder.create()
        .result(new MessageCorrelationResult("", 0))
        .properties(properties)
        .build();

    // When
    executable.activate(context);
    await().atMost(Duration.ofSeconds(5)).until(() -> context.getCorrelations().size() > 0);
    executable.deactivate();

    // Then
    assertEquals(1, context.getCorrelations().size());
    assertInstanceOf(RabbitMqInboundResult.class, context.getCorrelations().get(0));
    RabbitMqInboundResult castedResult = (RabbitMqInboundResult) context.getCorrelations().get(0);
    RabbitMqInboundMessage message = castedResult.getMessage();
    assertInstanceOf(Map.class, message.getBody());
    Map<String, Object> body = (Map<String, Object>) message.getBody();
    assertEquals("Hello World", body.get("value"));
  }

  private RabbitMqAuthentication getAuth() {
    RabbitMqAuthentication authentication = new RabbitMqAuthentication();
    authentication.setAuthType(RabbitMqAuthenticationType.credentials);
    authentication.setUserName(Authentication.USERNAME);
    authentication.setPassword(Authentication.PASSWORD);
    return authentication;
  }
}
