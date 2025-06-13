/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.discovery;

import static io.camunda.connector.agenticai.mcp.client.McpClientFunction.MCP_CLIENT_BASE_TYPE;
import static io.camunda.connector.agenticai.mcp.client.McpRemoteClientFunction.MCP_REMOTE_CLIENT_BASE_TYPE;
import static io.camunda.connector.agenticai.util.BpmnUtils.getElementDocumentation;

import io.camunda.connector.agenticai.adhoctoolsschema.resolver.GatewayToolDefinitionResolver;
import io.camunda.connector.agenticai.model.tool.GatewayToolDefinition;
import io.camunda.zeebe.model.bpmn.instance.FlowNode;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import java.util.List;

public class McpClientGatewayToolDefinitionResolver implements GatewayToolDefinitionResolver {

  private final List<String> mcpClientTaskDefinitionTypePrefixes;

  public McpClientGatewayToolDefinitionResolver() {
    this(List.of(MCP_CLIENT_BASE_TYPE, MCP_REMOTE_CLIENT_BASE_TYPE));
  }

  public McpClientGatewayToolDefinitionResolver(List<String> mcpClientTaskDefinitionTypePrefixes) {
    this.mcpClientTaskDefinitionTypePrefixes = mcpClientTaskDefinitionTypePrefixes;
  }

  @Override
  public List<GatewayToolDefinition> resolveGatewayToolDefinitions(List<FlowNode> elements) {
    return elements.stream()
        .filter(this::isMcpClient)
        .map(
            element ->
                GatewayToolDefinition.builder()
                    .type(McpClientGatewayToolHandler.GATEWAY_TYPE)
                    .name(element.getId())
                    .description(getElementDocumentation(element).orElse(null))
                    .build())
        .toList();
  }

  private boolean isMcpClient(FlowNode element) {
    return hasGatewayTypeExtensionProperty(element, McpClientGatewayToolHandler.GATEWAY_TYPE)
        || hasMcpClientServiceTaskType(element);
  }

  private boolean hasMcpClientServiceTaskType(FlowNode element) {
    if (!(element instanceof ServiceTask)) {
      return false;
    }

    final var taskDefinition = element.getSingleExtensionElement(ZeebeTaskDefinition.class);
    if (taskDefinition == null) {
      return false;
    }

    String taskType = taskDefinition.getType();
    return this.mcpClientTaskDefinitionTypePrefixes.stream().anyMatch(taskType::startsWith);
  }
}
