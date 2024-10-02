package io.camunda.connector.email.client.jakarta.inbound;

import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.email.inbound.model.EmailListenerConfig;
import jakarta.mail.event.MessageChangedEvent;
import jakarta.mail.event.MessageChangedListener;

public class CustomChangedListener implements MessageChangedListener {

  private final EventManager eventManager;
  private final InboundConnectorContext connectorContext;
  private final EmailListenerConfig config;

  public CustomChangedListener(
      InboundConnectorContext connectorContext, EmailListenerConfig config) {
    this.connectorContext = connectorContext;
    this.config = config;
    this.eventManager = new EventManager();
  }

  public static CustomChangedListener create(
      InboundConnectorContext context, EmailListenerConfig emailListenerConfig) {
    return new CustomChangedListener(context, emailListenerConfig);
  }

  @Override
  public void messageChanged(MessageChangedEvent event) {
    eventManager.processChangedEvent(event, connectorContext, config);
  }
}
