/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client;

import io.camunda.connector.agenticai.a2a.client.model.A2AClientAsToolOperationConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.A2AClientAsToolRequest;
import io.camunda.connector.agenticai.a2a.client.model.A2AClientOperationConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.A2AClientOperationConfiguration.FetchAgentCardOperationConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.A2AClientOperationConfiguration.SendMessageOperationConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.A2AClientRequest;
import io.camunda.connector.agenticai.a2a.client.model.result.A2AClientResult;
import io.camunda.connector.agenticai.a2a.discovery.A2AClientGatewayToolHandler;
import io.camunda.connector.agenticai.adhoctoolsschema.schema.GatewayToolDefinitionResolver;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import java.util.List;

@OutboundConnector(
    name = "A2A Client as Tool",
    inputVariables = {"data"},
    type = "io.camunda.agenticai:a2aclient_astool:0")
@ElementTemplate(
    id = "io.camunda.connectors.agenticai.a2a.client.astool.v0",
    name = "A2A Client as Tool(experimental)",
    description =
        "Agent-to-Agent (A2A) client, enabling discovering remote agents' Agent Cards as well as invoking remove agents. Compatible with 8.8.0-alpha8 or later. This connector can be used as a tool in the AI Agent connector.",
    engineVersion = "^8.8",
    version = 0,
    inputDataClass = A2AClientAsToolRequest.class,
    defaultResultVariable = "toolCallResult",
    propertyGroups = {
      @ElementTemplate.PropertyGroup(
          id = "connection",
          label = "HTTP Connection",
          tooltip =
              "Configure the HTTP connection to the remote A2A server for retrieving the Agent Card. Setting authentication headers is not supported yet."),
      @ElementTemplate.PropertyGroup(id = "operation", label = "Operation")
    },
    extensionProperties = {
      @ElementTemplate.ExtensionProperty(
          name = GatewayToolDefinitionResolver.GATEWAY_TYPE_EXTENSION,
          value = A2AClientGatewayToolHandler.GATEWAY_TYPE)
    },
    icon = "a2a-client.svg")
public class A2AClientAsToolFunction implements OutboundConnectorFunction {

  private final A2AClientHandler handler;

  public A2AClientAsToolFunction(A2AClientHandler handler) {
    this.handler = handler;
  }

  @Override
  public A2AClientResult execute(OutboundConnectorContext context) throws Exception {
    final A2AClientAsToolRequest request = context.bindVariables(A2AClientAsToolRequest.class);
    A2AClientRequest convertedRequest =
        new A2AClientRequest(
            new A2AClientRequest.A2AClientRequestData(
                request.data().connection(), convertOperation(request.data().operation())));
    return handler.handle(convertedRequest);
  }

  private A2AClientOperationConfiguration convertOperation(
      A2AClientAsToolOperationConfiguration operation) {
    switch (operation.operation()) {
      case FetchAgentCardOperationConfiguration.FETCH_AGENT_CARD_ID -> {
        return new FetchAgentCardOperationConfiguration();
      }
      case SendMessageOperationConfiguration.SEND_MESSAGE_ID -> {
        if (operation.params() == null || !operation.params().containsKey("message")) {
          throw new IllegalArgumentException(
              "The 'message' parameter is required for the '%s' operation."
                  .formatted(operation.operation()));
        }
        return new SendMessageOperationConfiguration(
            new SendMessageOperationConfiguration.Parameters(
                operation.params().get("message").toString(), List.of()),
            operation.timeout());
      }
      default ->
          throw new IllegalArgumentException(
              "Unsupported operation: '%s'".formatted(operation.operation()));
    }
  }
}
