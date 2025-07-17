/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.schema;

import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElement;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolsSchemaResponse;
import io.camunda.connector.agenticai.adhoctoolsschema.resolver.GatewayToolDefinitionResolver;
import io.camunda.connector.agenticai.model.tool.GatewayToolDefinition;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import java.util.List;

public class AdHocToolDefinitionResolverImpl implements AdHocToolDefinitionResolver {
  private final List<GatewayToolDefinitionResolver> gatewayToolDefinitionResolvers;
  private final AdHocToolSchemaGenerator schemaGenerator;

  public AdHocToolDefinitionResolverImpl(
      List<GatewayToolDefinitionResolver> gatewayToolDefinitionResolvers,
      AdHocToolSchemaGenerator schemaGenerator) {
    this.gatewayToolDefinitionResolvers = gatewayToolDefinitionResolvers;
    this.schemaGenerator = schemaGenerator;
  }

  @Override
  public AdHocToolsSchemaResponse resolveToolDefinitions(List<AdHocToolElement> elements) {
    final var gatewayToolDefinitions =
        gatewayToolDefinitionResolvers.stream()
            .flatMap(resolver -> resolver.resolveGatewayToolDefinitions(elements).stream())
            .toList();

    final var gatewayFlowNodeIds =
        gatewayToolDefinitions.stream().map(GatewayToolDefinition::name).toList();

    // map all non-gateway tool elements to tool definitions
    final var toolDefinitions =
        elements.stream()
            .filter(toolElement -> !gatewayFlowNodeIds.contains(toolElement.elementId()))
            .map(this::createToolDefinition)
            .toList();

    return new AdHocToolsSchemaResponse(toolDefinitions, gatewayToolDefinitions);
  }

  private ToolDefinition createToolDefinition(AdHocToolElement element) {
    return ToolDefinition.builder()
        .name(element.elementId())
        .description(element.documentationWithNameFallback())
        .inputSchema(schemaGenerator.generateToolSchema(element))
        .build();
  }
}
