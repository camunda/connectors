/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.resolver;

import io.camunda.connector.agenticai.model.tool.GatewayToolDefinition;
import io.camunda.zeebe.model.bpmn.instance.FlowNode;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeProperties;
import java.util.List;

/**
 * Given a list of elements identified as root activities within an ad-hoc sub-process, resolves
 * gateway activities which are not an individual tool but rather a gateway for a set of tools. Most
 * prominent use-case is MCP.
 *
 * <p>This resolver's responsibility is to identify supported gateway activities and to map them to
 * a gateway tool definition which can be handled by a matching {@link
 * io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandler}.
 */
public interface GatewayToolDefinitionResolver {
  String GATEWAY_TYPE_EXTENSION = "io.camunda.agenticai.gateway.type";

  List<GatewayToolDefinition> resolveGatewayToolDefinitions(List<FlowNode> elements);

  default boolean hasGatewayTypeExtensionProperty(FlowNode element, String expectedTypeValue) {
    final var extensionProperties = element.getSingleExtensionElement(ZeebeProperties.class);
    return extensionProperties != null
        && extensionProperties.getProperties().stream()
            .anyMatch(
                property ->
                    GATEWAY_TYPE_EXTENSION.equals(property.getName())
                        && property.getValue().equals(expectedTypeValue));
  }
}
