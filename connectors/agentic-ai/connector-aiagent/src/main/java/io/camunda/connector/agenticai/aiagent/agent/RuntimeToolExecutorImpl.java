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
import io.camunda.connector.agenticai.aiagent.document.DocumentParameterResolver;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentId;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolCallImpl;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolContextImpl;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolRegistry;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Runs the calls of runtime tools one after the other, in the order of the calls. */
public class RuntimeToolExecutorImpl implements RuntimeToolExecutor {

  private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeToolExecutorImpl.class);

  private final RuntimeToolRegistry runtimeToolRegistry;
  private final ObjectMapper objectMapper;
  private final ObjectMapper argumentsMapper;

  /**
   * @param objectMapper the mapper that reads a document from its reference and, in a copy, binds
   *     the arguments of a call
   */
  public RuntimeToolExecutorImpl(
      RuntimeToolRegistry runtimeToolRegistry, ObjectMapper objectMapper) {
    this.runtimeToolRegistry = runtimeToolRegistry;
    this.objectMapper = objectMapper;
    this.argumentsMapper = RuntimeToolCallImpl.argumentsMapper(objectMapper);
  }

  @Override
  public List<RuntimeToolExecution> execute(
      AgentExecutionContext executionContext,
      AgentContext agentContext,
      List<ToolCall> runtimeToolCalls,
      Set<DocumentId> documentIdsWithContent) {
    return runtimeToolCalls.stream()
        .map(toolCall -> execute(executionContext, agentContext, documentIdsWithContent, toolCall))
        .toList();
  }

  private RuntimeToolExecution execute(
      AgentExecutionContext executionContext,
      AgentContext agentContext,
      Set<DocumentId> documentIdsWithContent,
      ToolCall toolCall) {
    final var startedAt = OffsetDateTime.now();
    final var tool =
        runtimeToolRegistry.findEnabled(executionContext, agentContext, toolCall.name());
    final var result =
        tool.isPresent()
            ? run(executionContext, agentContext, documentIdsWithContent, tool.get(), toolCall)
            : unansweredByATool(agentContext, toolCall);
    final var completedAt = OffsetDateTime.now();
    return new RuntimeToolExecution(toolCall, result, startedAt, completedAt);
  }

  /**
   * The error result of a call that no enabled runtime tool answers: the call of a tool of the
   * process that was rejected for its document parameters gets the rejection, any other call the
   * statement that the tool is not enabled.
   */
  private static RuntimeToolResult unansweredByATool(AgentContext agentContext, ToolCall toolCall) {
    return switch (DocumentParameterResolver.resolveProcessCall(agentContext, toolCall)) {
      case DocumentParameterResolver.Resolution.Invalid invalid ->
          RuntimeToolResult.error(invalid.message());
      case DocumentParameterResolver.Resolution.Resolved ignored ->
          RuntimeToolResult.error("The tool '%s' is not enabled.".formatted(toolCall.name()));
    };
  }

  /**
   * Runs the call of a tool with the documents of its document parameters resolved to {@link
   * io.camunda.connector.agenticai.aiagent.model.document.DocumentId}s. A call with an invalid
   * document parameter does not reach the tool and gets an error result.
   */
  private RuntimeToolResult run(
      AgentExecutionContext executionContext,
      AgentContext agentContext,
      Set<DocumentId> documentIdsWithContent,
      RuntimeTool tool,
      ToolCall toolCall) {
    return switch (DocumentParameterResolver.resolveRuntimeCall(
        agentContext, toolCall, tool.definition())) {
      case DocumentParameterResolver.Resolution.Invalid invalid ->
          RuntimeToolResult.error(invalid.message());
      case DocumentParameterResolver.Resolution.Resolved resolved ->
          call(
              executionContext,
              agentContext,
              documentIdsWithContent,
              tool,
              toolCall,
              resolved.arguments());
    };
  }

  /** Calls the tool. A {@link RuntimeToolException} becomes an error result. */
  private RuntimeToolResult call(
      AgentExecutionContext executionContext,
      AgentContext agentContext,
      Set<DocumentId> documentIdsWithContent,
      RuntimeTool tool,
      ToolCall toolCall,
      Map<String, Object> arguments) {
    final var runtimeToolCall =
        new RuntimeToolCallImpl(toolCall.id(), toolCall.name(), arguments, argumentsMapper);
    final var runtimeToolContext =
        RuntimeToolContextImpl.of(
            executionContext, agentContext, documentIdsWithContent, objectMapper);

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
}
