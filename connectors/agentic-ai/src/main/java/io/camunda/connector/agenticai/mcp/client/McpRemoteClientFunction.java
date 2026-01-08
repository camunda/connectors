/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client;

import static io.camunda.connector.agenticai.mcp.client.model.McpConnectorModeConfiguration.ToolModeConfiguration.AI_AGENT_TOOL_ID;

import io.camunda.connector.agenticai.adhoctoolsschema.schema.GatewayToolDefinitionResolver;
import io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientRequest;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientResult;
import io.camunda.connector.agenticai.mcp.discovery.McpClientGatewayToolHandler;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.TemplateProperty;

@OutboundConnector(
    name = "MCP Remote Client",
    inputVariables = {"data"},
    type = "io.camunda.agenticai:mcpremoteclient:1")
@ElementTemplate(
    id = "io.camunda.connectors.agenticai.mcp.remoteclient.v0",
    name = "MCP Remote Client (early access)",
    description = "MCP (Model Context Protocol) client, operating on temporary remote connections.",
    engineVersion = "^8.9",
    version = 2,
    inputDataClass = McpRemoteClientRequest.class,
    defaultResultVariable = "toolCallResult",
    propertyGroups = {
      @ElementTemplate.PropertyGroup(
          id = "transport",
          label = "Transport",
          tooltip = "Configure the connection to the remote MCP server."),
      @ElementTemplate.PropertyGroup(id = "authentication", label = "Authentication"),
      @ElementTemplate.PropertyGroup(id = "options", label = "Options"),
      @ElementTemplate.PropertyGroup(
          id = "connectorMode",
          label = "Connector mode",
          tooltip =
              "Select how this connector is used. When the connector is used as an AI agent tool, select the AI Agent tool mode."),
      @ElementTemplate.PropertyGroup(id = "operation", label = "Operation"),
      @ElementTemplate.PropertyGroup(id = "tools", label = "Tools", openByDefault = false),
    },
    extensionProperties = {
      @ElementTemplate.ExtensionProperty(
          name = GatewayToolDefinitionResolver.GATEWAY_TYPE_EXTENSION,
          value = McpClientGatewayToolHandler.GATEWAY_TYPE,
          condition =
              @TemplateProperty.PropertyCondition(
                  property = "data.connectorMode.type",
                  equals = AI_AGENT_TOOL_ID)),
    },
    icon = "mcp-client.svg")
public class McpRemoteClientFunction implements OutboundConnectorFunction {

  private final McpRemoteClientHandler handler;

  public McpRemoteClientFunction(McpRemoteClientHandler handler) {
    this.handler = handler;
  }

  @Override
  public McpClientResult execute(OutboundConnectorContext context) {
    final McpRemoteClientRequest request = context.bindVariables(McpRemoteClientRequest.class);
    return handler.handle(context, request);
  }
}
