/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
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
