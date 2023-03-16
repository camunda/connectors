package io.camunda.connector.kafkainbound;

import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;

@InboundConnector(name = "KAFKAINBOUND", type = "io.camunda:connector-kafka-inbound:1")
public class KafkaExecutable implements InboundConnectorExecutable {

    private KafkaSubscription subscription;
    private InboundConnectorContext connectorContext;

    @Override
    public void activate(InboundConnectorContext connectorContext) {
        KafkaConnectorProperties props = connectorContext.getPropertiesAsType(KafkaConnectorProperties.class);

        connectorContext.replaceSecrets(props);
        connectorContext.validate(props);

        this.connectorContext = connectorContext;

        subscription = new KafkaSubscription(
                props.getSender(), props.getMessagesPerMinute(), this::onEvent);
    }

    @Override
    public void deactivate() {
        subscription.stop();
    }

    private void onEvent(KafkaSubscriptionEvent rawEvent) {
        KafkaConnectorEvent connectorEvent = new KafkaConnectorEvent(rawEvent);
        connectorContext.correlate(connectorEvent);
    }
}
