/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client;

import io.camunda.connector.agenticai.mcp.client.model.McpClientRequest;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientResult;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;

@OutboundConnector(
    name = "MCP Client (experimental)",
    inputVariables = {"data"},
    type = McpClientFunction.MCP_CLIENT_TYPE)
@ElementTemplate(
    id = "io.camunda.connectors.agenticai.mcp.client.v0",
    name = "MCP Client (experimental)",
    description =
        "MCP (Model Context Protocol) client using MCP connections configured on the connector runtime. Only supports tool operations. Compatible with 8.8.0-alpha6 or later.",
    engineVersion = "^8.8",
    version = 0,
    inputDataClass = McpClientRequest.class,
    propertySources = McpClientPropertySource.class,
    defaultResultVariable = "toolCallResult",
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "client", label = "MCP Client"),
      @ElementTemplate.PropertyGroup(id = "tools", label = "Tools"),
      @ElementTemplate.PropertyGroup(id = "operation", label = "Operation", openByDefault = false)
    },
    icon = "mcp-client.svg")
public class McpClientFunction implements OutboundConnectorFunction {

  public static final String MCP_CLIENT_BASE_TYPE = "io.camunda.agenticai:mcpclient";
  public static final String MCP_CLIENT_TYPE = MCP_CLIENT_BASE_TYPE + ":0";

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
