/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.tools.schema;

import io.camunda.connector.agenticai.aiagent.api.tool.GatewayToolDefinitionResolver;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.tool.AdHocToolElement;
import io.camunda.connector.agenticai.aiagent.model.tool.GatewayToolDefinition;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.tools.schema.model.AdHocToolsSchemaResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Converts a list of {@link AdHocToolElement} to a {@link AdHocToolsSchemaResponse} containing tool
 * definitions and gateway tool definitions.
 *
 * <p>Invokes registered {@link GatewayToolDefinitionResolver} implementations to handle gateway
 * tool definitions.
 */
public class AdHocToolsSchemaResolverImpl implements AdHocToolsSchemaResolver {

  /**
   * The extension property of a tool activity that sets the document delivery of the documents the
   * tool returns: {@code reference} or {@code content}.
   */
  static final String DOCUMENT_DELIVERY_PROPERTY = "io.camunda.agenticai.documents.delivery";

  private final List<GatewayToolDefinitionResolver> gatewayToolDefinitionResolvers;
  private final AdHocToolSchemaGenerator schemaGenerator;

  public AdHocToolsSchemaResolverImpl(
      List<GatewayToolDefinitionResolver> gatewayToolDefinitionResolvers,
      AdHocToolSchemaGenerator schemaGenerator) {
    this.gatewayToolDefinitionResolvers = gatewayToolDefinitionResolvers;
    this.schemaGenerator = schemaGenerator;
  }

  @Override
  public AdHocToolsSchemaResponse resolveAdHocToolsSchema(List<AdHocToolElement> elements) {
    if (elements.isEmpty()) {
      return AdHocToolsSchemaResponse.builder().build();
    }

    final var gatewayToolDefinitions =
        gatewayToolDefinitionResolvers.stream()
            .flatMap(resolver -> resolver.resolveGatewayToolDefinitions(elements).stream())
            .toList();

    final var gatewayFlowNodeIds =
        gatewayToolDefinitions.stream()
            .map(GatewayToolDefinition::name)
            .collect(Collectors.toSet());

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
        .documentDelivery(documentDelivery(element))
        .build();
  }

  /**
   * The delivery set by the extension property {@link #DOCUMENT_DELIVERY_PROPERTY} of the element,
   * ignoring case and surrounding blanks. An absent or blank property and the value {@code
   * reference} are the default delivery, so they are all {@code null}.
   *
   * @throws AdHocToolSchemaGenerationException if the value names no delivery
   */
  private static @Nullable DocumentDelivery documentDelivery(AdHocToolElement element) {
    final var properties = element.properties();
    final var value = properties == null ? null : properties.get(DOCUMENT_DELIVERY_PROPERTY);
    if (value == null || value.isBlank()) {
      return null;
    }
    final var name = value.strip();
    final var delivery =
        Arrays.stream(DocumentDelivery.values())
            .filter(candidate -> candidate.name().equalsIgnoreCase(name))
            .findFirst()
            .orElseThrow(() ->
                new AdHocToolSchemaGenerationException(
                    "Failed to resolve ad-hoc tool element '%s'. Property '%s': The document delivery '%s' is not allowed, use one of: %s."
                        .formatted(
                            element.elementId(),
                            DOCUMENT_DELIVERY_PROPERTY,
                            value,
                            Arrays.stream(DocumentDelivery.values())
                                .map(candidate -> candidate.name().toLowerCase(Locale.ROOT))
                                .collect(Collectors.joining(", ")))));
    return delivery == DocumentDelivery.REFERENCE ? null : delivery;
  }
}
