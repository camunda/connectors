/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeTool;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolContext;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolException;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolResult;
import io.camunda.connector.agenticai.aiagent.document.DocumentParameterResolver;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolKind;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Executes the calls of runtime tools one after the other, in the order of the calls. */
public class RuntimeToolsImpl implements RuntimeTools {

  private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeToolsImpl.class);

  private record Registration(RuntimeTool tool, ToolDefinition definition) {}

  private final Map<String, Registration> registrationsByName;
  private final ObjectMapper argumentsMapper;

  /**
   * @throws IllegalStateException if two tools have the same name, a name is blank or a definition
   *     is not of kind {@link ToolKind#RUNTIME}
   */
  public RuntimeToolsImpl(List<RuntimeTool> tools, ObjectMapper objectMapper) {
    this.registrationsByName = register(tools);
    this.argumentsMapper = RuntimeToolCallImpl.argumentsMapper(objectMapper);
  }

  @Override
  public List<ToolDefinition> enabledDefinitions(
      AgentExecutionContext executionContext, AgentContext agentContext) {
    if (!agentContext.supports(AgentFeatures.RUNTIME_TOOLS)) {
      return List.of();
    }
    final var context = new RuntimeToolContextImpl(executionContext, agentContext);
    return registrationsByName.values().stream()
        .filter(registration -> registration.tool().isEnabled(context))
        .map(Registration::definition)
        .toList();
  }

  @Override
  public List<RuntimeToolExecution> execute(
      AgentExecutionContext executionContext, AgentContext agentContext, List<ToolCall> calls) {
    final var context = new RuntimeToolContextImpl(executionContext, agentContext);
    return calls.stream().map(call -> execute(context, call)).toList();
  }

  private RuntimeToolExecution execute(RuntimeToolContext context, ToolCall toolCall) {
    final var startedAt = OffsetDateTime.now(ZoneId.systemDefault());
    final var result =
        findEnabled(context, toolCall.name())
            .map(tool -> callTool(context, tool, toolCall))
            .orElseGet(() ->
                RuntimeToolResult.error(
                    "The tool '%s' is not available.".formatted(toolCall.name())));
    final var completedAt = OffsetDateTime.now(ZoneId.systemDefault());
    return new RuntimeToolExecution(
        toolCall, resultContent(toolCall, result.content(), completedAt), startedAt, completedAt);
  }

  private Optional<RuntimeTool> findEnabled(RuntimeToolContext context, String name) {
    if (!context.agentContext().supports(AgentFeatures.RUNTIME_TOOLS)) {
      return Optional.empty();
    }
    return Optional.ofNullable(registrationsByName.get(name))
        .map(Registration::tool)
        .filter(tool -> tool.isEnabled(context));
  }

  private RuntimeToolResult callTool(
      RuntimeToolContext context, RuntimeTool tool, ToolCall toolCall) {
    return switch (DocumentParameterResolver.resolveRuntimeCall(
        context.agentContext(), toolCall, tool.definition())) {
      case DocumentParameterResolver.Resolution.Invalid invalid ->
          RuntimeToolResult.error(invalid.message());
      case DocumentParameterResolver.Resolution.Resolved resolved ->
          callTool(context, tool, toolCall, resolved.arguments());
    };
  }

  private RuntimeToolResult callTool(
      RuntimeToolContext context,
      RuntimeTool tool,
      ToolCall toolCall,
      Map<String, Object> arguments) {
    final var runtimeToolCall =
        new RuntimeToolCallImpl(toolCall.id(), toolCall.name(), arguments, argumentsMapper);

    RuntimeToolResult result;
    try {
      result = tool.call(context, runtimeToolCall);
    } catch (RuntimeToolException e) {
      LOGGER.debug("Runtime tool '{}' rejected the call '{}'", toolCall.name(), toolCall.id(), e);
      result = RuntimeToolResult.error(e.getMessage());
    }
    return Objects.requireNonNull(
        result, "The runtime tool '%s' returned no result".formatted(toolCall.name()));
  }

  private static ToolCallResultContent resultContent(
      ToolCall toolCall, List<Content> content, OffsetDateTime completedAt) {
    return ToolCallResultContent.builder()
        .id(toolCall.id())
        .name(toolCall.name())
        .content(content)
        .completedAt(completedAt)
        .build();
  }

  private static Map<String, Registration> register(List<RuntimeTool> tools) {
    final var registrations = new LinkedHashMap<String, Registration>();
    for (final var tool : tools) {
      final var definition = tool.definition();
      final var name = definition.name();
      if (name.isBlank()) {
        throw new IllegalStateException("A runtime tool must have a non-blank name");
      }
      if (definition.kind() != ToolKind.RUNTIME) {
        throw new IllegalStateException(
            "The runtime tool '%s' must declare the kind %s in its definition."
                .formatted(name, ToolKind.RUNTIME));
      }
      if (registrations.putIfAbsent(name, new Registration(tool, definition)) != null) {
        throw new IllegalStateException(
            "More than one runtime tool is named '%s'; runtime tool names must be unique."
                .formatted(name));
      }
    }
    return registrations;
  }
}
