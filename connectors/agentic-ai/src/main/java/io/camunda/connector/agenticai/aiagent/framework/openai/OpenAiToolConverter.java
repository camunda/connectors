/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.openai;

import com.openai.core.JsonValue;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import com.openai.models.chat.completions.ChatCompletionTool;
import com.openai.models.responses.FunctionTool;
import com.openai.models.responses.Tool;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import java.util.List;
import java.util.Map;

/**
 * Converts {@link ToolDefinition}s into OpenAI {@link ChatCompletionTool}s. Shared between the Chat
 * Completions and Responses native impls — both endpoints accept the same {@code tools[]} JSON
 * shape.
 */
public final class OpenAiToolConverter {

  private OpenAiToolConverter() {}

  public static List<ChatCompletionTool> toTools(List<ToolDefinition> definitions) {
    if (definitions == null || definitions.isEmpty()) {
      return List.of();
    }
    return definitions.stream().map(OpenAiToolConverter::toTool).toList();
  }

  public static ChatCompletionTool toTool(ToolDefinition definition) {
    final var function = FunctionDefinition.builder().name(definition.name());
    if (definition.description() != null) {
      function.description(definition.description());
    }
    function.parameters(toFunctionParameters(definition.inputSchema()));

    return ChatCompletionTool.ofFunction(
        ChatCompletionFunctionTool.builder().function(function.build()).build());
  }

  private static FunctionParameters toFunctionParameters(Map<String, Object> schemaMap) {
    final var builder = FunctionParameters.builder();
    if (schemaMap == null || schemaMap.isEmpty()) {
      return builder.putAdditionalProperty("type", JsonValue.from("object")).build();
    }
    schemaMap.forEach((key, value) -> builder.putAdditionalProperty(key, JsonValue.from(value)));
    return builder.build();
  }

  /**
   * Variant for the Responses API, which uses a different (but JSON-equivalent) Tool/Parameters
   * shape.
   */
  public static List<Tool> toResponsesTools(List<ToolDefinition> definitions) {
    if (definitions == null || definitions.isEmpty()) {
      return List.of();
    }
    return definitions.stream().map(OpenAiToolConverter::toResponsesTool).toList();
  }

  public static Tool toResponsesTool(ToolDefinition definition) {
    final var builder = FunctionTool.builder().name(definition.name()).strict(false);
    if (definition.description() != null) {
      builder.description(definition.description());
    }
    builder.parameters(toResponsesParameters(definition.inputSchema()));
    return Tool.ofFunction(builder.build());
  }

  private static FunctionTool.Parameters toResponsesParameters(Map<String, Object> schemaMap) {
    final var builder = FunctionTool.Parameters.builder();
    if (schemaMap == null || schemaMap.isEmpty()) {
      return builder.putAdditionalProperty("type", JsonValue.from("object")).build();
    }
    schemaMap.forEach((key, value) -> builder.putAdditionalProperty(key, JsonValue.from(value)));
    return builder.build();
  }
}
