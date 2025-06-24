/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.discovery;

import static io.camunda.connector.agenticai.util.BpmnUtils.getElementDocumentation;

import io.camunda.connector.agenticai.adhoctoolsschema.resolver.GatewayToolDefinitionResolver;
import io.camunda.connector.agenticai.model.tool.GatewayToolDefinition;
import io.camunda.zeebe.model.bpmn.instance.FlowNode;
import java.util.List;

public class McpClientGatewayToolDefinitionResolver implements GatewayToolDefinitionResolver {

  @Override
  public List<GatewayToolDefinition> resolveGatewayToolDefinitions(List<FlowNode> elements) {
    return elements.stream()
        .filter(
            element ->
                hasGatewayTypeExtensionProperty(element, McpClientGatewayToolHandler.GATEWAY_TYPE))
        .map(
            element ->
                GatewayToolDefinition.builder()
                    .type(McpClientGatewayToolHandler.GATEWAY_TYPE)
                    .name(element.getId())
                    .description(getElementDocumentation(element).orElse(null))
                    .build())
        .toList();
  }
}
