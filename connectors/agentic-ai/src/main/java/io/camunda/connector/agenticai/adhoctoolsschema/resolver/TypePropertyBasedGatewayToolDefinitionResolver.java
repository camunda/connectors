/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.resolver;

import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElement;
import io.camunda.connector.agenticai.model.tool.GatewayToolDefinition;
import java.util.List;

public class TypePropertyBasedGatewayToolDefinitionResolver
    implements GatewayToolDefinitionResolver {
  private final String gatewayType;

  public TypePropertyBasedGatewayToolDefinitionResolver(String gatewayType) {
    if (gatewayType == null || gatewayType.isBlank()) {
      throw new IllegalArgumentException("Gateway type must not be null or empty");
    }

    this.gatewayType = gatewayType;
  }

  @Override
  public List<GatewayToolDefinition> resolveGatewayToolDefinitions(
      List<AdHocToolElement> elements) {
    return elements.stream()
        .filter(this::hasMatchingGatewayTypeProperty)
        .map(this::createGatewayToolDefinition)
        .toList();
  }

  private GatewayToolDefinition createGatewayToolDefinition(AdHocToolElement element) {
    return GatewayToolDefinition.builder()
        .type(gatewayType)
        .name(element.elementId())
        .description(element.documentationWithNameFallback())
        .build();
  }

  private boolean hasMatchingGatewayTypeProperty(AdHocToolElement element) {
    return element.properties() != null
        && element.properties().entrySet().stream()
            .anyMatch(
                property ->
                    GATEWAY_TYPE_EXTENSION.equals(property.getKey())
                        && gatewayType.equals(property.getValue()));
  }
}
