/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.inbound;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorProperties;
import io.camunda.connector.api.inbound.SubscriptionInboundConnector;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@InboundConnector(type = "rabbitmq", name = "RABBIT_MQ")
public class RabbitMqInboundConnectorSubscription implements SubscriptionInboundConnector {

  private static final Logger LOG = LoggerFactory.getLogger(RabbitMqInboundConnectorSubscription.class);

  public RabbitMqInboundConnectorSubscription() {
  }

  @Override
  public void activate(InboundConnectorProperties genericProperties, final InboundConnectorContext context) throws Exception {
    // TODO: What happens if this fails because of some error of the third party - when would we retry connecting?
    // Would that be with the next polling interval as this is not marked as active? Double check!
    final RabbitMqConnectorProperties properties = new RabbitMqConnectorProperties(genericProperties);

    ConnectionFactory connectionFactory = new ConnectionFactory();
    connectionFactory.setUri(properties.getUri());
    Connection connection = connectionFactory.newConnection();
    Channel channel = connection.createChannel();

    DeliverCallback deliverCallback = (consumerTag, delivery) -> {
      HashMap<String, Object> message = new HashMap<>();
      message.put("envelope", delivery.getEnvelope());
      message.put("properties", delivery.getProperties());
      message.put("body", new String(delivery.getBody(), StandardCharsets.UTF_8));

      executeOnIncomingMessage(properties, context, message);
      LOG.trace("Received AMQP message %1", message);
    };

    channel.basicConsume(properties.getQueueName(), deliverCallback, consumerTag -> { /*Canel Callback*/ });
  }

  /**
   * Copied from InboundWebhookController - maybe better make some code reusable in connector-runtime-util
   */
  private ProcessInstanceEvent executeOnIncomingMessage(RabbitMqConnectorProperties connectorProperties, InboundConnectorContext context, Map<String, Object> message) {
    final Map<String, Object> variables = extractVariables(connectorProperties, message);

    return context.zeebeClient()
            .newCreateInstanceCommand()
            .bpmnProcessId(connectorProperties.getBpmnProcessId())
            .version(connectorProperties.getVersion())
            .variables(variables)
            .send()
            .join();
  }

  private Map<String, Object> extractVariables(RabbitMqConnectorProperties connectorProperties, Map<String, Object> message) {
    String variableMapping = connectorProperties.getVariableMapping();
    if (variableMapping == null) {
      return message;
    }
    // TODO: Implement
    throw new RuntimeException("Not yet implemented");
    //return feelEngine.evaluate(variableMapping, message);
  }

  @Override
  public void deactivate() throws Exception {}
}
