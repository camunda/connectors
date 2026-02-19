/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.outbound;

import io.camunda.connector.agenticai.a2a.client.common.A2aConstants;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aClientResponse;
import io.camunda.connector.agenticai.a2a.client.outbound.model.A2aClientRequest;
import io.camunda.connector.agenticai.adhoctoolsschema.schema.GatewayToolDefinitionResolver;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.TemplateProperty;

@OutboundConnector(
    name = "A2A Client",
    inputVariables = {"data"},
    type = "io.camunda.agenticai:a2aclient:0")
@ElementTemplate(
    id = "io.camunda.connectors.agenticai.a2a.client.v0",
    name = "A2A Client (early access)",
    description =
        "Agent-to-Agent (A2A) client, enabling discovering remote agents' Agent Cards as well as sending messages to remote agents.",
    engineVersion = "^8.9",
    version = 0,
    inputDataClass = A2aClientRequest.class,
    propertyGroups = {
      @ElementTemplate.PropertyGroup(
          id = "connection",
          label = "HTTP Connection",
          tooltip =
              "Configure the HTTP connection to the remote A2A server for retrieving the Agent Card. Setting authentication headers is not supported yet."),
      @ElementTemplate.PropertyGroup(
          id = "connectorMode",
          label = "Connector mode",
          tooltip =
              "Select how this connector is used. When the connector is used as an AI agent tool, select the <code>AI Agent tool</code> mode."),
      @ElementTemplate.PropertyGroup(id = "operation", label = "Operation")
    },
    extensionProperties = {
      @ElementTemplate.ExtensionProperty(
          name = GatewayToolDefinitionResolver.GATEWAY_TYPE_EXTENSION,
          value = A2aConstants.A2A_GATEWAY_TYPE,
          condition =
              @TemplateProperty.PropertyCondition(
                  property = "data.connectorMode.type",
                  equals = "aiAgentTool"))
    },
    icon = "a2a-client.svg")
public class A2aClientOutboundConnectorFunction implements OutboundConnectorFunction {

  private final A2aClientRequestHandler handler;

  public A2aClientOutboundConnectorFunction(A2aClientRequestHandler handler) {
    this.handler = handler;
  }

  @Override
  public A2aClientResponse execute(OutboundConnectorContext context) {
    final A2aClientRequest request = context.bindVariables(A2aClientRequest.class);
    return handler.handle(request);
  }
}
