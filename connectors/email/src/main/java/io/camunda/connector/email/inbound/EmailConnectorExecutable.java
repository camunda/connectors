/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.inbound;

import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.email.core.jakarta.JakartaEmailListener;
import io.camunda.connector.email.core.jakarta.JakartaSessionFactory;
import io.camunda.connector.email.inbound.model.EmailProperties;
import io.camunda.connector.generator.dsl.BpmnType;
import io.camunda.connector.generator.java.annotation.ElementTemplate;

@InboundConnector(name = "Email Consumer", type = "io.camunda:connector-email-inbound:1")
@ElementTemplate(
    id = "io.camunda.connectors.email",
    name = "Email Event Connector",
    icon = "icon.svg",
    version = 1,
    inputDataClass = EmailProperties.class,
    description = "Consume emails",
    documentationRef =
        "https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/email",
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "authentication", label = "Authentication"),
      @ElementTemplate.PropertyGroup(id = "protocol", label = "Imap Details"),
      @ElementTemplate.PropertyGroup(id = "listenerInfos", label = "Listener information"),
    },
    elementTypes = {
      @ElementTemplate.ConnectorElementType(
          appliesTo = BpmnType.START_EVENT,
          elementType = BpmnType.MESSAGE_START_EVENT,
          templateIdOverride = "io.camunda.connectors.inbound.EmailMessageStart.v1",
          templateNameOverride = "Email Message Start Event Connector"),
      @ElementTemplate.ConnectorElementType(
          appliesTo = {BpmnType.INTERMEDIATE_THROW_EVENT, BpmnType.INTERMEDIATE_CATCH_EVENT},
          elementType = BpmnType.INTERMEDIATE_CATCH_EVENT,
          templateIdOverride = "io.camunda.connectors.inbound.EmailIntermediate.v1",
          templateNameOverride = "Email Intermediate Catch Event Connector"),
      @ElementTemplate.ConnectorElementType(
          appliesTo = BpmnType.BOUNDARY_EVENT,
          elementType = BpmnType.BOUNDARY_EVENT,
          templateIdOverride = "io.camunda.connectors.inbound.EmailBoundary.v1",
          templateNameOverride = "Email Boundary Event Connector")
    })
public class EmailConnectorExecutable
    implements InboundConnectorExecutable<InboundConnectorContext> {

  @Override
  public void activate(InboundConnectorContext context) {
    JakartaEmailListener.create(context, new JakartaSessionFactory()).startListener();
    context.reportHealth(Health.up());
  }

  @Override
  public void deactivate() {}
}
