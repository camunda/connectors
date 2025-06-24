/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client;

import io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientRequest;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientResult;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;

@OutboundConnector(
    name = "MCP Remote Client (experimental)",
    inputVariables = {"data"},
    type = McpRemoteClientFunction.MCP_REMOTE_CLIENT_TYPE)
@ElementTemplate(
    id = "io.camunda.connectors.agenticai.mcp.remoteclient.v0",
    name = "MCP Remote Client (experimental)",
    description =
        "MCP (Model Context Protocol) client, operating on temporary remote connections. Only supports tool operations. Compatible with 8.8.0-alpha6 or later.",
    engineVersion = "^8.8",
    version = 0,
    inputDataClass = McpRemoteClientRequest.class,
    propertySources = McpClientPropertySource.class,
    defaultResultVariable = "toolCallResult",
    propertyGroups = {
      @ElementTemplate.PropertyGroup(
          id = "connection",
          label = "HTTP Connection",
          tooltip =
              "Configure the HTTP/SSE connection to the remote MCP server. Setting authentication headers is not supported yet."),
      @ElementTemplate.PropertyGroup(id = "tools", label = "Tools"),
      @ElementTemplate.PropertyGroup(id = "operation", label = "Operation")
    },
    icon = "mcp-client.svg")
public class McpRemoteClientFunction implements OutboundConnectorFunction {

  public static final String MCP_REMOTE_CLIENT_BASE_TYPE = "io.camunda.agenticai:mcpremoteclient";
  public static final String MCP_REMOTE_CLIENT_TYPE = MCP_REMOTE_CLIENT_BASE_TYPE + ":0";

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
