/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client;

import io.camunda.connector.agenticai.mcp.client.model.McpClientRequest;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;

@OutboundConnector(
    name = "MCP Client (alpha)",
    inputVariables = {"annotations", "data"},
    type = McpClientFunction.MCP_CLIENT_TYPE)
@ElementTemplate(
    id = "io.camunda.connectors.agenticai.mcp.client.v0",
    name = "MCP Client (alpha)",
    description = "MCP (Model Context Protocol) Client which is able to handle MCP tool calls.",
    engineVersion = "^8.8",
    version = 0,
    inputDataClass = McpClientRequest.class,
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "client", label = "MCP Client"),
      @ElementTemplate.PropertyGroup(id = "operation", label = "Operation")
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
  public Object execute(OutboundConnectorContext context) throws Exception {
    final McpClientRequest request = context.bindVariables(McpClientRequest.class);
    return handler.handle(request);
  }
}
