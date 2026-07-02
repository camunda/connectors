/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model;

import static io.camunda.connector.agenticai.mcp.client.model.McpStandaloneOperationConfiguration.CallToolOperationConfiguration.CALL_TOOL_ID;
import static io.camunda.connector.agenticai.mcp.client.model.McpStandaloneOperationConfiguration.GetPromptOperationConfiguration.GET_PROMPT_ID;
import static io.camunda.connector.agenticai.mcp.client.model.McpStandaloneOperationConfiguration.ListPromptsOperationConfiguration.LIST_PROMPTS_ID;
import static io.camunda.connector.agenticai.mcp.client.model.McpStandaloneOperationConfiguration.ListResourceTemplatesOperationConfiguration.LIST_RESOURCE_TEMPLATES_ID;
import static io.camunda.connector.agenticai.mcp.client.model.McpStandaloneOperationConfiguration.ListResourcesOperationConfiguration.LIST_RESOURCES_ID;
import static io.camunda.connector.agenticai.mcp.client.model.McpStandaloneOperationConfiguration.ListToolsOperationConfiguration.LIST_TOOLS_ID;
import static io.camunda.connector.agenticai.mcp.client.model.McpStandaloneOperationConfiguration.ReadResourceOperationConfiguration.READ_RESOURCE_ID;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

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
      name = LIST_RESOURCE_TEMPLATES_ID),
  @JsonSubTypes.Type(
      value = McpStandaloneOperationConfiguration.ReadResourceOperationConfiguration.class,
      name = READ_RESOURCE_ID),
  @JsonSubTypes.Type(
      value = McpStandaloneOperationConfiguration.ListPromptsOperationConfiguration.class,
      name = LIST_PROMPTS_ID),
  @JsonSubTypes.Type(
      value = McpStandaloneOperationConfiguration.GetPromptOperationConfiguration.class,
      name = GET_PROMPT_ID)
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
        McpStandaloneOperationConfiguration.ListResourceTemplatesOperationConfiguration,
        McpStandaloneOperationConfiguration.ReadResourceOperationConfiguration,
        McpStandaloneOperationConfiguration.ListPromptsOperationConfiguration,
        McpStandaloneOperationConfiguration.GetPromptOperationConfiguration {

  String method();

  Optional<Map<String, Object>> params();

  // Unlike params(), meta() is a direct pass-through, so it's not Optional-wrapped.
  @Nullable Map<String, Object> meta();

  @TemplateSubType(id = LIST_TOOLS_ID, label = "List Tools")
  record ListToolsOperationConfiguration(
      @FEEL
          @TemplateProperty(
              id = "toolsListMeta",
              group = "operation",
              label = "Metadata",
              description = "MCP <code>_meta</code> parameters to be passed to the MCP request.",
              tooltip =
                  "Forwarded unmodified as the <code>_meta</code> field of the MCP request. Can be used, for example, to scope requests to a specific product version. See the <a href=\"https://modelcontextprotocol.io/specification/2025-11-25/basic/index#_meta\">MCP specification</a> for details.",
              feel = FeelMode.required,
              optional = true)
          @Nullable Map<String, Object> meta)
      implements McpStandaloneOperationConfiguration {

    @TemplateProperty(ignore = true)
    public static final String LIST_TOOLS_ID = "tools/list";

    public ListToolsOperationConfiguration() {
      this(null);
    }

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
              feel = FeelMode.optional,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          @NotBlank
          String toolName,
      @FEEL
          @TemplateProperty(
              group = "operation",
              label = "Tool arguments",
              description = "The arguments to pass to the tool.",
              feel = FeelMode.required,
              optional = true)
          Map<String, Object> toolArguments,
      @FEEL
          @TemplateProperty(
              id = "toolsCallMeta",
              group = "operation",
              label = "Metadata",
              description = "MCP <code>_meta</code> parameters to be passed to the MCP request.",
              tooltip =
                  "Forwarded unmodified as the <code>_meta</code> field of the MCP request. Can be used, for example, to scope requests to a specific product version. See the <a href=\"https://modelcontextprotocol.io/specification/2025-11-25/basic/index#_meta\">MCP specification</a> for details.",
              feel = FeelMode.required,
              optional = true)
          @Nullable Map<String, Object> meta)
      implements McpStandaloneOperationConfiguration {

    @TemplateProperty(ignore = true)
    public static final String CALL_TOOL_ID = "tools/call";

    public CallToolOperationConfiguration(String toolName, Map<String, Object> toolArguments) {
      this(toolName, toolArguments, null);
    }

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
  record ListResourcesOperationConfiguration(
      @FEEL
          @TemplateProperty(
              id = "resourcesListMeta",
              group = "operation",
              label = "Metadata",
              description = "MCP <code>_meta</code> parameters to be passed to the MCP request.",
              tooltip =
                  "Forwarded unmodified as the <code>_meta</code> field of the MCP request. Can be used, for example, to scope requests to a specific product version. See the <a href=\"https://modelcontextprotocol.io/specification/2025-11-25/basic/index#_meta\">MCP specification</a> for details.",
              feel = FeelMode.required,
              optional = true)
          @Nullable Map<String, Object> meta)
      implements McpStandaloneOperationConfiguration {

    @TemplateProperty(ignore = true)
    public static final String LIST_RESOURCES_ID = "resources/list";

    public ListResourcesOperationConfiguration() {
      this(null);
    }

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
  record ListResourceTemplatesOperationConfiguration(
      @FEEL
          @TemplateProperty(
              id = "resourcesTemplatesListMeta",
              group = "operation",
              label = "Metadata",
              description = "MCP <code>_meta</code> parameters to be passed to the MCP request.",
              tooltip =
                  "Forwarded unmodified as the <code>_meta</code> field of the MCP request. Can be used, for example, to scope requests to a specific product version. See the <a href=\"https://modelcontextprotocol.io/specification/2025-11-25/basic/index#_meta\">MCP specification</a> for details.",
              feel = FeelMode.required,
              optional = true)
          @Nullable Map<String, Object> meta)
      implements McpStandaloneOperationConfiguration {

    @TemplateProperty(ignore = true)
    public static final String LIST_RESOURCE_TEMPLATES_ID = "resources/templates/list";

    public ListResourceTemplatesOperationConfiguration() {
      this(null);
    }

    @Override
    public String method() {
      return LIST_RESOURCE_TEMPLATES_ID;
    }

    @Override
    public Optional<Map<String, Object>> params() {
      return Optional.empty();
    }
  }

  @TemplateSubType(id = READ_RESOURCE_ID, label = "Read Resource")
  record ReadResourceOperationConfiguration(
      @FEEL
          @TemplateProperty(
              group = "operation",
              label = "Resource URI",
              description = "The URI of the resource to read.",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          @NotBlank
          String resourceUri,
      @FEEL
          @TemplateProperty(
              id = "resourcesReadMeta",
              group = "operation",
              label = "Metadata",
              description = "MCP <code>_meta</code> parameters to be passed to the MCP request.",
              tooltip =
                  "Forwarded unmodified as the <code>_meta</code> field of the MCP request. Can be used, for example, to scope requests to a specific product version. See the <a href=\"https://modelcontextprotocol.io/specification/2025-11-25/basic/index#_meta\">MCP specification</a> for details.",
              feel = FeelMode.required,
              optional = true)
          @Nullable Map<String, Object> meta)
      implements McpStandaloneOperationConfiguration {

    @TemplateProperty(ignore = true)
    public static final String READ_RESOURCE_ID = "resources/read";

    public ReadResourceOperationConfiguration(String resourceUri) {
      this(resourceUri, null);
    }

    @Override
    public String method() {
      return READ_RESOURCE_ID;
    }

    @Override
    public Optional<Map<String, Object>> params() {
      return Optional.of(Map.of("uri", resourceUri));
    }
  }

  @TemplateSubType(id = LIST_PROMPTS_ID, label = "List Prompts")
  record ListPromptsOperationConfiguration(
      @FEEL
          @TemplateProperty(
              id = "promptsListMeta",
              group = "operation",
              label = "Metadata",
              description = "MCP <code>_meta</code> parameters to be passed to the MCP request.",
              tooltip =
                  "Forwarded unmodified as the <code>_meta</code> field of the MCP request. Can be used, for example, to scope requests to a specific product version. See the <a href=\"https://modelcontextprotocol.io/specification/2025-11-25/basic/index#_meta\">MCP specification</a> for details.",
              feel = FeelMode.required,
              optional = true)
          @Nullable Map<String, Object> meta)
      implements McpStandaloneOperationConfiguration {

    @TemplateProperty(ignore = true)
    public static final String LIST_PROMPTS_ID = "prompts/list";

    public ListPromptsOperationConfiguration() {
      this(null);
    }

    @Override
    public String method() {
      return LIST_PROMPTS_ID;
    }

    @Override
    public Optional<Map<String, Object>> params() {
      return Optional.empty();
    }
  }

  @TemplateSubType(id = GET_PROMPT_ID, label = "Get Prompt")
  record GetPromptOperationConfiguration(
      @FEEL
          @TemplateProperty(
              group = "operation",
              label = "Prompt name",
              description = "The name of the prompt to get.",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          @NotBlank
          String promptName,
      @FEEL
          @TemplateProperty(
              group = "operation",
              label = "Prompt arguments",
              description = "The arguments to pass to the prompt generation.",
              feel = FeelMode.required,
              optional = true)
          Map<String, Object> promptArguments,
      @FEEL
          @TemplateProperty(
              id = "promptsGetMeta",
              group = "operation",
              label = "Metadata",
              description = "MCP <code>_meta</code> parameters to be passed to the MCP request.",
              tooltip =
                  "Forwarded unmodified as the <code>_meta</code> field of the MCP request. Can be used, for example, to scope requests to a specific product version. See the <a href=\"https://modelcontextprotocol.io/specification/2025-11-25/basic/index#_meta\">MCP specification</a> for details.",
              feel = FeelMode.required,
              optional = true)
          @Nullable Map<String, Object> meta)
      implements McpStandaloneOperationConfiguration {

    @TemplateProperty(ignore = true)
    public static final String GET_PROMPT_ID = "prompts/get";

    public GetPromptOperationConfiguration(String promptName, Map<String, Object> promptArguments) {
      this(promptName, promptArguments, null);
    }

    @Override
    public String method() {
      return GET_PROMPT_ID;
    }

    @Override
    public Optional<Map<String, Object>> params() {
      if (promptArguments == null || promptArguments.isEmpty()) {
        return Optional.of(Map.of("name", promptName));
      }
      return Optional.of(Map.of("name", promptName, "arguments", promptArguments));
    }
  }
}
