/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolsSchemaResponse;
import io.camunda.connector.agenticai.adhoctoolsschema.schema.AdHocToolsSchemaResolver;
import io.camunda.connector.agenticai.aiagent.localtool.LocalToolRegistry;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.tool.GatewayToolDefinition;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.api.error.ConnectorException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentToolsResolverImpl implements AgentToolsResolver {

  private static final Logger LOGGER = LoggerFactory.getLogger(AgentToolsResolverImpl.class);

  private static final Collector<ToolDefinition, ?, LinkedHashMap<String, ToolDefinition>>
      ORDERED_MAP_COLLECTOR =
          Collectors.toMap(ToolDefinition::name, td -> td, (a, b) -> a, LinkedHashMap::new);

  private final AdHocToolsSchemaResolver toolsSchemaResolver;
  private final GatewayToolHandlerRegistry gatewayToolHandlers;
  private final LocalToolRegistry localToolRegistry;

  public AgentToolsResolverImpl(
      AdHocToolsSchemaResolver toolsSchemaResolver,
      GatewayToolHandlerRegistry gatewayToolHandlers,
      LocalToolRegistry localToolRegistry) {
    this.toolsSchemaResolver = toolsSchemaResolver;
    this.gatewayToolHandlers = gatewayToolHandlers;
    this.localToolRegistry = localToolRegistry;
  }

  @Override
  public AdHocToolsSchemaResponse loadAdHocToolsSchema(
      AgentExecutionContext executionContext, AgentContext agentContext) {
    final var adHocToolsSchema =
        toolsSchemaResolver.resolveAdHocToolsSchema(executionContext.toolElements());
    if (agentContext.supportsDocuments()) {
      rejectReservedToolNames(adHocToolsSchema);
    }
    return adHocToolsSchema;
  }

  /**
   * Rejects an ad-hoc or gateway tool element named like a registered local tool, whether that tool
   * is available for this agent or not: availability can change with the configuration of a running
   * agent, a name must not turn into a collision then. An agent instance created without the
   * documents feature reserves no name: it offers no local tool and must keep running its process.
   */
  private void rejectReservedToolNames(AdHocToolsSchemaResponse adHocToolsSchema) {
    final var reservedNames = localToolRegistry.toolNames();
    Stream.concat(
            adHocToolsSchema.toolDefinitions().stream().map(ToolDefinition::name),
            adHocToolsSchema.gatewayToolDefinitions().stream().map(GatewayToolDefinition::name))
        .filter(reservedNames::contains)
        .findFirst()
        .ifPresent(
            name -> {
              throw new ConnectorException(
                  AgentErrorCodes.ERROR_CODE_RESERVED_TOOL_NAME,
                  "The tool name '%s' is reserved for a built-in tool of the AI Agent. Rename the ad-hoc sub-process element with this ID."
                      .formatted(name));
            });
  }

  @Override
  public AgentContext updateToolDefinitions(
      AgentExecutionContext executionContext, AgentContext agentContext) {
    LOGGER.warn(
        "Agent context tool definitions may be outdated compared to the current process definition. Re-loading tool definitions.");

    final var adHocToolsSchema = loadAdHocToolsSchema(executionContext, agentContext);
    validateGatewayToolDefinitionChanges(agentContext, adHocToolsSchema);

    return updateChangedToolDefinitions(agentContext, adHocToolsSchema);
  }

  private void validateGatewayToolDefinitionChanges(
      AgentContext agentContext, AdHocToolsSchemaResponse adHocToolsSchema) {
    final var gatewayToolHandlerUpdates =
        gatewayToolHandlers.resolveUpdatedGatewayToolDefinitions(
            agentContext, adHocToolsSchema.gatewayToolDefinitions());
    if (gatewayToolHandlerUpdates.isEmpty()) {
      return;
    }

    final var changesSummary =
        gatewayToolHandlerUpdates.entrySet().stream()
            .map(
                entry -> {
                  final var updates = entry.getValue();

                  final var parts = new ArrayList<String>();
                  if (CollectionUtils.isNotEmpty(updates.added())) {
                    parts.add("added: " + String.join(", ", updates.added()));
                  }

                  if (CollectionUtils.isNotEmpty(updates.removed())) {
                    parts.add("removed: " + String.join(", ", updates.removed()));
                  }

                  return "%s [%s]".formatted(entry.getKey(), String.join("; ", parts));
                })
            .collect(Collectors.joining(", "));

    throw new ConnectorException(
        AgentErrorCodes.ERROR_CODE_MIGRATION_GATEWAY_TOOL_DEFINITIONS_CHANGED,
        """
              Gateway tool definitions have changed, most likely due to a process migration.
              Adding or removing gateway tool definitions to a running AI Agent is currently not supported.
              Please restore gateway tool definitions to the previous state to continue agent execution.
              Changes: %s"""
            .formatted(changesSummary));
  }

  private AgentContext updateChangedToolDefinitions(
      AgentContext agentContext, AdHocToolsSchemaResponse adHocToolsSchema) {
    final var existingToolDefinitions =
        agentContext.toolDefinitions().stream().collect(ORDERED_MAP_COLLECTOR);
    final var existingNonGatewayToolDefinitions =
        agentContext.toolDefinitions().stream()
            .filter(toolDefinition -> !gatewayToolHandlers.isGatewayManaged(toolDefinition.name()))
            .collect(ORDERED_MAP_COLLECTOR);
    final var newToolDefinitions =
        adHocToolsSchema.toolDefinitions().stream().collect(ORDERED_MAP_COLLECTOR);

    final var removedToolDefinitions =
        existingNonGatewayToolDefinitions.values().stream()
            .filter(toolDefinition -> !newToolDefinitions.containsKey(toolDefinition.name()))
            .toList();
    if (!removedToolDefinitions.isEmpty()) {
      final var removedToolNames =
          removedToolDefinitions.stream().map(ToolDefinition::name).toList();
      throw new ConnectorException(
          AgentErrorCodes.ERROR_CODE_MIGRATION_MISSING_TOOLS,
          """
              The AI Agent references tools that are no longer defined, most likely due to a process migration.
              Removing or renaming existing tools is currently not supported.
              Please re-add the following tools to continue agent execution: %s"""
              .formatted(String.join(", ", removedToolNames)));
    }

    // overwrite existing tool definitions which have changed + add new ones
    final var updatedToolDefinitions = new LinkedHashMap<>(existingToolDefinitions);
    newToolDefinitions
        .values()
        .forEach(
            newToolDefinition -> {
              if (existingNonGatewayToolDefinitions.containsKey(newToolDefinition.name())) {
                final var existingToolDefinition =
                    existingNonGatewayToolDefinitions.get(newToolDefinition.name());

                if (!existingToolDefinition.equals(newToolDefinition)) {
                  LOGGER.info(
                      "Updating tool definition '{}' after process migration.",
                      newToolDefinition.name());
                  updatedToolDefinitions.put(newToolDefinition.name(), newToolDefinition);
                }
              } else {
                LOGGER.info(
                    "Adding new tool definition '{}' to agent context after process migration.",
                    newToolDefinition.name());
                updatedToolDefinitions.put(newToolDefinition.name(), newToolDefinition);
              }
            });

    return agentContext.withToolDefinitions(updatedToolDefinitions.values().stream().toList());
  }
}
