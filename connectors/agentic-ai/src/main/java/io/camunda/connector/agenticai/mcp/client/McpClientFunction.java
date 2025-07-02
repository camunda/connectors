/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client;

import io.camunda.connector.agenticai.adhoctoolsschema.resolver.GatewayToolDefinitionResolver;
import io.camunda.connector.agenticai.mcp.client.model.McpClientRequest;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientResult;
import io.camunda.connector.agenticai.mcp.discovery.McpClientGatewayToolHandler;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;

@OutboundConnector(
    name = "MCP Client",
    inputVariables = {"data"},
    type = "io.camunda.agenticai:mcpclient:0")
@ElementTemplate(
    id = "io.camunda.connectors.agenticai.mcp.client.v0",
    name = "MCP Client (experimental)",
    description =
        "MCP (Model Context Protocol) client using MCP connections configured on the connector runtime. Only supports tool operations. Compatible with 8.8.0-alpha6 or later.",
    engineVersion = "^8.8",
    version = 0,
    inputDataClass = McpClientRequest.class,
    defaultResultVariable = "toolCallResult",
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "client", label = "MCP Client"),
      @ElementTemplate.PropertyGroup(id = "tools", label = "Tools"),
      @ElementTemplate.PropertyGroup(id = "operation", label = "Operation", openByDefault = false)
    },
    extensionProperties = {
      @ElementTemplate.ExtensionProperty(
          name = GatewayToolDefinitionResolver.GATEWAY_TYPE_EXTENSION,
          value = McpClientGatewayToolHandler.GATEWAY_TYPE),
    },
    icon = "mcp-client.svg")
public class McpClientFunction implements OutboundConnectorFunction {

  private final McpClientHandler handler;

  public McpClientFunction(McpClientHandler handler) {
    this.handler = handler;
  }

  @Override
  public McpClientResult execute(OutboundConnectorContext context) {
    final McpClientRequest request = context.bindVariables(McpClientRequest.class);
    return handler.handle(context, request);
  }
}
