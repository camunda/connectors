/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model;

import static io.camunda.connector.agenticai.mcp.client.model.McpStandaloneOperationConfiguration.CallToolOperationConfiguration.CALL_TOOL_ID;
import static io.camunda.connector.agenticai.mcp.client.model.McpStandaloneOperationConfiguration.ListResourceTemplatesOperationConfiguration.LIST_RESOURCE_TEMPLATES_ID;
import static io.camunda.connector.agenticai.mcp.client.model.McpStandaloneOperationConfiguration.ListResourcesOperationConfiguration.LIST_RESOURCES_ID;
import static io.camunda.connector.agenticai.mcp.client.model.McpStandaloneOperationConfiguration.ListToolsOperationConfiguration.LIST_TOOLS_ID;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.Optional;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(
      value = McpStandaloneOperationConfiguration.ListToolsOperationConfiguration.class,
      name = LIST_TOOLS_ID),
  @JsonSubTypes.Type(
      value = McpStandaloneOperationConfiguration.CallToolOperationConfiguration.class,
      name = CALL_TOOL_ID),
  @JsonSubTypes.Type(
      value = McpStandaloneOperationConfiguration.ListResourcesOperationConfiguration.class,
      name = LIST_RESOURCES_ID),
  @JsonSubTypes.Type(
      value = McpStandaloneOperationConfiguration.ListResourceTemplatesOperationConfiguration.class,
      name = LIST_RESOURCE_TEMPLATES_ID)
})
@TemplateDiscriminatorProperty(
    group = "operation",
    label = "Operation",
    name = "type",
    description = "The type of operation to perform.",
    defaultValue = LIST_TOOLS_ID)
public sealed interface McpStandaloneOperationConfiguration
    permits McpStandaloneOperationConfiguration.ListToolsOperationConfiguration,
        McpStandaloneOperationConfiguration.CallToolOperationConfiguration,
        McpStandaloneOperationConfiguration.ListResourcesOperationConfiguration,
        McpStandaloneOperationConfiguration.ListResourceTemplatesOperationConfiguration {

  String method();

  Optional<Map<String, Object>> params();

  @TemplateSubType(id = LIST_TOOLS_ID, label = "List Tools")
  record ListToolsOperationConfiguration() implements McpStandaloneOperationConfiguration {

    @TemplateProperty(ignore = true)
    public static final String LIST_TOOLS_ID = "tools/list";

    @Override
    public String method() {
      return LIST_TOOLS_ID;
    }

    @Override
    public Optional<Map<String, Object>> params() {
      return Optional.empty();
    }
  }

  @TemplateSubType(id = CALL_TOOL_ID, label = "Call Tool")
  record CallToolOperationConfiguration(
      @FEEL
          @TemplateProperty(
              group = "operation",
              label = "Tool name",
              description = "The name of the tool to call.",
              type = TemplateProperty.PropertyType.String,
              feel = Property.FeelMode.optional,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          @NotBlank
          String toolName,
      @FEEL
          @TemplateProperty(
              group = "operation",
              label = "Tool arguments",
              description = "The arguments to pass to the tool.",
              feel = Property.FeelMode.required,
              optional = true)
          Map<String, Object> toolArguments)
      implements McpStandaloneOperationConfiguration {

    @TemplateProperty(ignore = true)
    public static final String CALL_TOOL_ID = "tools/call";

    @Override
    public String method() {
      return CALL_TOOL_ID;
    }

    @Override
    public Optional<Map<String, Object>> params() {
      if (toolArguments == null || toolArguments.isEmpty()) {
        return Optional.of(Map.of("name", toolName));
      }
      return Optional.of(Map.of("name", toolName, "arguments", toolArguments));
    }
  }

  @TemplateSubType(id = LIST_RESOURCES_ID, label = "List Resources")
  record ListResourcesOperationConfiguration() implements McpStandaloneOperationConfiguration {

    @TemplateProperty(ignore = true)
    public static final String LIST_RESOURCES_ID = "resources/list";

    @Override
    public String method() {
      return LIST_RESOURCES_ID;
    }

    @Override
    public Optional<Map<String, Object>> params() {
      return Optional.empty();
    }
  }

  @TemplateSubType(id = LIST_RESOURCE_TEMPLATES_ID, label = "List Resource Templates")
  record ListResourceTemplatesOperationConfiguration()
      implements McpStandaloneOperationConfiguration {

    @TemplateProperty(ignore = true)
    public static final String LIST_RESOURCE_TEMPLATES_ID = "resources/templates/list";

    @Override
    public String method() {
      return LIST_RESOURCE_TEMPLATES_ID;
    }

    @Override
    public Optional<Map<String, Object>> params() {
      return Optional.empty();
    }
  }
}
