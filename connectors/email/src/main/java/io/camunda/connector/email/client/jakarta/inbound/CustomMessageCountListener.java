/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.client.jakarta.inbound;

import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.email.inbound.model.EmailListenerConfig;
import jakarta.mail.event.MessageCountEvent;
import jakarta.mail.event.MessageCountListener;

public class CustomMessageCountListener implements MessageCountListener {

  private final EventManager eventManager;
  private final InboundConnectorContext connectorContext;
  private final EmailListenerConfig emailListenerConfig;

  public CustomMessageCountListener(
      EventManager eventManager,
      InboundConnectorContext connectorContext,
      EmailListenerConfig emailListenerConfig) {
    this.eventManager = eventManager;
    this.connectorContext = connectorContext;
    this.emailListenerConfig = emailListenerConfig;
  }

  public static CustomMessageCountListener create(
      InboundConnectorContext connectorContext, EmailListenerConfig emailListenerConfig) {
    return new CustomMessageCountListener(
        new EventManager(), connectorContext, emailListenerConfig);
  }

  @Override
  public void messagesAdded(MessageCountEvent event) {
    this.eventManager.processNewEvent(event, connectorContext, emailListenerConfig);
  }

  @Override
  public void messagesRemoved(MessageCountEvent e) {}
}
