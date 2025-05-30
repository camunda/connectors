/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client;

import io.camunda.connector.agenticai.mcp.client.model.McpClientRemoteRequest;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientResult;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;

@OutboundConnector(
    name = "MCP Remote Client (alpha)",
    inputVariables = {"data"},
    type = McpClientRemoteFunction.MCP_CLIENT_REMOTE_TYPE)
@ElementTemplate(
    id = "io.camunda.connectors.agenticai.mcp.client.remote.v0",
    name = "MCP Remote Client (alpha)",
    description =
        "MCP (Model Context Protocol) Client, directly initiating remote connections. Less performant than the MCP Client which is configured on the runtime.",
    engineVersion = "^8.8",
    version = 0,
    inputDataClass = McpClientRemoteRequest.class,
    defaultResultVariable = "toolCallResult",
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "connection", label = "HTTP Connection"),
      @ElementTemplate.PropertyGroup(id = "tools", label = "Tools"),
      @ElementTemplate.PropertyGroup(id = "operation", label = "Operation")
    },
    icon = "mcp-client.svg")
public class McpClientRemoteFunction implements OutboundConnectorFunction {

  public static final String MCP_CLIENT_REMOTE_BASE_TYPE = "io.camunda.agenticai:mcpclientremote";
  public static final String MCP_CLIENT_REMOTE_TYPE = MCP_CLIENT_REMOTE_BASE_TYPE + ":0";

  private final McpClientRemoteHandler handler;

  public McpClientRemoteFunction(McpClientRemoteHandler handler) {
    this.handler = handler;
  }

  @Override
  public McpClientResult execute(OutboundConnectorContext context) {
    final McpClientRemoteRequest request = context.bindVariables(McpClientRemoteRequest.class);
    return handler.handle(context, request);
  }
}
