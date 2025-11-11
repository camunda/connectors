/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model;

import static io.camunda.connector.agenticai.mcp.client.model.McpConnectorModeConfiguration.StandaloneModeConfiguration.STANDALONE_ID;
import static io.camunda.connector.agenticai.mcp.client.model.McpConnectorModeConfiguration.ToolModeConfiguration.AI_AGENT_TOOL_ID;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(
      value = McpConnectorModeConfiguration.ToolModeConfiguration.class,
      name = AI_AGENT_TOOL_ID),
  @JsonSubTypes.Type(
      value = McpConnectorModeConfiguration.StandaloneModeConfiguration.class,
      name = STANDALONE_ID)
})
@TemplateDiscriminatorProperty(
    group = "connectorMode",
    label = "Connector mode",
    name = "type",
    description = "Select how this connector is used.",
    defaultValue = AI_AGENT_TOOL_ID)
public sealed interface McpConnectorModeConfiguration
    permits McpConnectorModeConfiguration.StandaloneModeConfiguration,
        McpConnectorModeConfiguration.ToolModeConfiguration {

  @TemplateSubType(id = AI_AGENT_TOOL_ID, label = "AI Agent tool")
  record ToolModeConfiguration(
      @NestedProperties(group = "operation") @Valid @NotNull
          McpClientOperationConfiguration toolOperation)
      implements McpConnectorModeConfiguration {

    @TemplateProperty(ignore = true)
    public static final String AI_AGENT_TOOL_ID = "aiAgentTool";
  }

  @TemplateSubType(id = STANDALONE_ID, label = "Standalone")
  record StandaloneModeConfiguration(
      @NestedProperties(group = "operation") @Valid @NotNull
          McpStandaloneOperationConfiguration operation)
      implements McpConnectorModeConfiguration {

    @TemplateProperty(ignore = true)
    public static final String STANDALONE_ID = "standalone";
  }
}
