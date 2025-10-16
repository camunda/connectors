package io.camunda.connector.rabbitmq.outbound;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.rabbitmq.outbound.model.RabbitMqDeleteRequest;
import io.camunda.connector.rabbitmq.supplier.ConnectionFactorySupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OutboundConnector(
    name = "RabbitMQ Delete Queue",
    inputVariables = {"authentication", "routing"},
    type = "io.camunda:connector-rabbitmq-delete:1")
@ElementTemplate(
    engineVersion = "^8.3",
    id = "io.camunda.connectors.RabbitMQ.Delete.v1",
    name = "RabbitMQ Delete Queue Connector",
    description = "Delete a queue in RabbitMQ",
    inputDataClass = RabbitMqDeleteRequest.class,
    version = 1,
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "authentication", label = "Authentication"),
      @ElementTemplate.PropertyGroup(id = "routing", label = "Routing")
    },
    documentationRef =
        "https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/rabbitmq/?rabbitmq=outbound",
    icon = "icon.svg")
public class RabbitMqDeleteFunction implements OutboundConnectorFunction {

  private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMqDeleteFunction.class);

  private final ConnectionFactorySupplier connectionFactorySupplier;

  public RabbitMqDeleteFunction() {
    this.connectionFactorySupplier = new ConnectionFactorySupplier();
  }

  // constructor for tests/injection
  public RabbitMqDeleteFunction(final ConnectionFactorySupplier connectionFactorySupplier) {
    this.connectionFactorySupplier = connectionFactorySupplier;
  }

  @Override
  public Object execute(final OutboundConnectorContext context) throws Exception {
    var request = context.bindVariables(RabbitMqDeleteRequest.class);
    return executeConnector(request);
  }

  private RabbitMqDeleteResult executeConnector(final RabbitMqDeleteRequest request)
      throws Exception {
    final var routing = request.routing();
    final String queueName = routing.queueName().trim();
    final boolean ifUnused = routing.ifUnused();
    final boolean ifEmpty = routing.ifEmpty();

    try (Connection connection =
            connectionFactorySupplier
                .createFactory(request.authentication(), routing.routingData())
                .newConnection();
        Channel channel = connection.createChannel()) {

      // perform delete (returns AMQP.Queue.DeleteOk)
      var deleteOk = channel.queueDelete(queueName, ifUnused, ifEmpty);
      boolean deleted = deleteOk != null;

      if (deleted) {
        LOGGER.info("Queue '{}' deleted (ifUnused={}, ifEmpty={})", queueName, ifUnused, ifEmpty);
        return RabbitMqDeleteResult.success();
      } else {
        LOGGER.warn(
            "Attempt to delete queue '{}' returned null DeleteOk (ifUnused={}, ifEmpty={})",
            queueName,
            ifUnused,
            ifEmpty);
        return RabbitMqDeleteResult.failure("delete-returned-null");
      }
    } catch (Exception e) {
      LOGGER.error("Error deleting queue '{}': {}", queueName, e.getMessage(), e);
      return RabbitMqDeleteResult.failure(e.getMessage());
    }
  }
}
