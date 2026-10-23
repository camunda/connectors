/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeTool;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolException;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolResult;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolCallImpl;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolContextImpl;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolRegistry;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Runs the calls of runtime tools one after the other, in the order of the calls. */
public class RuntimeToolExecutorImpl implements RuntimeToolExecutor {

  private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeToolExecutorImpl.class);

  private final RuntimeToolRegistry runtimeToolRegistry;
  private final ObjectMapper argumentsMapper;

  /**
   * @param objectMapper the mapper that, in a copy, binds the arguments of a call
   */
  public RuntimeToolExecutorImpl(
      RuntimeToolRegistry runtimeToolRegistry, ObjectMapper objectMapper) {
    this.runtimeToolRegistry = runtimeToolRegistry;
    this.argumentsMapper = RuntimeToolCallImpl.argumentsMapper(objectMapper);
  }

  @Override
  public List<RuntimeToolExecution> execute(
      AgentExecutionContext executionContext,
      AgentContext agentContext,
      List<ToolCall> runtimeToolCalls) {
    return runtimeToolCalls.stream()
        .map(toolCall -> execute(executionContext, agentContext, toolCall))
        .toList();
  }

  private RuntimeToolExecution execute(
      AgentExecutionContext executionContext, AgentContext agentContext, ToolCall toolCall) {
    final var startedAt = OffsetDateTime.now();
    final var tool =
        runtimeToolRegistry.findEnabled(executionContext, agentContext, toolCall.name());
    final var result =
        tool.isPresent()
            ? run(executionContext, agentContext, tool.get(), toolCall)
            : RuntimeToolResult.error("The tool '%s' is not enabled.".formatted(toolCall.name()));
    final var completedAt = OffsetDateTime.now();
    return new RuntimeToolExecution(
        toolCall, resultContent(toolCall, result.content(), completedAt), startedAt, completedAt);
  }

  /** Runs the call of a tool. A {@link RuntimeToolException} becomes an error result. */
  private RuntimeToolResult run(
      AgentExecutionContext executionContext,
      AgentContext agentContext,
      RuntimeTool tool,
      ToolCall toolCall) {
    final var runtimeToolCall =
        new RuntimeToolCallImpl(
            toolCall.id(),
            toolCall.name(),
            toolCall.arguments() == null ? Map.of() : toolCall.arguments(),
            argumentsMapper);
    final var runtimeToolContext = new RuntimeToolContextImpl(executionContext, agentContext);

    RuntimeToolResult result;
    try {
      result = tool.call(runtimeToolCall, runtimeToolContext);
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
}
