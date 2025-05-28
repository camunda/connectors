/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.discovery.configuration;

import static io.camunda.connector.agenticai.mcp.client.McpClientFunction.MCP_CLIENT_BASE_TYPE;

import io.camunda.connector.agenticai.adhoctoolsschema.resolver.GatewayToolDefinitionResolver;
import io.camunda.connector.agenticai.model.tool.GatewayToolDefinition;
import io.camunda.connector.agenticai.util.BpmnUtils;
import io.camunda.zeebe.model.bpmn.instance.FlowNode;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class McpClientGatewayToolDefinitionResolver implements GatewayToolDefinitionResolver {

  public static final String PROPERTY_TYPE = "type";
  public static final String PROPERTY_GATEWAY_TYPE = "gatewayType";

  private final List<String> taskDefinitionTypePrefixes;

  public McpClientGatewayToolDefinitionResolver() {
    this(List.of(MCP_CLIENT_BASE_TYPE));
  }

  public McpClientGatewayToolDefinitionResolver(List<String> taskDefinitionTypePrefixes) {
    this.taskDefinitionTypePrefixes = taskDefinitionTypePrefixes;
  }

  @Override
  public List<GatewayToolDefinition> resolveGatewayToolDefinitions(List<FlowNode> elements) {
    return elements.stream()
        .map(this::extractServiceTask)
        .filter(Objects::nonNull)
        .filter(this::isMcpClient)
        .map(
            serviceTaskElement ->
                new GatewayToolDefinition(
                    serviceTaskElement.element().getId(),
                    BpmnUtils.getElementDocumentation(serviceTaskElement.element()).orElse(null),
                    Map.of(
                        PROPERTY_TYPE,
                        serviceTaskElement.taskDefinition.getType(),
                        PROPERTY_GATEWAY_TYPE,
                        "mcpClient")))
        .toList();
  }

  private boolean isMcpClient(ServiceTaskElement serviceTaskElement) {
    String type = serviceTaskElement.taskDefinition.getType();
    return this.taskDefinitionTypePrefixes.stream().anyMatch(type::startsWith);
  }

  private ServiceTaskElement extractServiceTask(FlowNode element) {
    if (!(element instanceof ServiceTask)) {
      return null;
    }

    final var taskDefinition = element.getSingleExtensionElement(ZeebeTaskDefinition.class);
    if (taskDefinition != null) {
      return new ServiceTaskElement(element, taskDefinition);
    }

    return null;
  }

  private record ServiceTaskElement(FlowNode element, ZeebeTaskDefinition taskDefinition) {}
}
