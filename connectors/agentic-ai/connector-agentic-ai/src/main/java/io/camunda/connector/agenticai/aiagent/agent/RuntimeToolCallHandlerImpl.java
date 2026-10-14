/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.document.DocumentParameterResolver;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentConversation;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameterSchema;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeTool;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolCall;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolException;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolRegistry;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolResult;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the calls of available {@link RuntimeTool}s of a batch and validates the document parameters
 * of the calls of process tools. A call that gets a runtime result is answered by the agent, every
 * other call remains for the process.
 */
public class RuntimeToolCallHandlerImpl implements RuntimeToolCallHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeToolCallHandlerImpl.class);

  private final RuntimeToolRegistry runtimeToolRegistry;

  public RuntimeToolCallHandlerImpl(RuntimeToolRegistry runtimeToolRegistry) {
    this.runtimeToolRegistry = runtimeToolRegistry;
  }

  @Override
  public List<ToolDefinition> availableToolDefinitions(
      AgentExecutionContext executionContext, AgentContext agentContext) {
    return runtimeToolRegistry.availableToolDefinitions(executionContext, agentContext);
  }

  @Override
  public RuntimeToolCallSplit handle(
      AgentExecutionContext executionContext,
      AgentConversation conversation,
      List<ToolCall> toolCalls) {
    if (toolCalls.isEmpty()) {
      return RuntimeToolCallSplit.allRemaining(conversation, toolCalls);
    }

    final var completedAt = OffsetDateTime.now();
    final var runtimeResults = new ArrayList<ToolCallResult>();
    final var remainingCalls = new ArrayList<ToolCall>();
    var workingConversation = conversation;
    for (final var toolCall : toolCalls) {
      final var tool =
          runtimeToolRegistry.findAvailable(
              toolCall.name(), executionContext, workingConversation.toAgentContext());
      final RuntimeToolResult result;
      if (tool.isPresent()) {
        result = run(tool.get(), toolCall, executionContext, workingConversation);
      } else {
        // an agent instance without the documents feature has no document parameters
        final var rejection =
            workingConversation.supportsDocuments()
                ? validateDocumentParameters(toolCall, workingConversation)
                : null;
        if (rejection == null) {
          remainingCalls.add(toolCall);
          continue;
        }
        result = rejection;
      }

      final var blocks = result.contentBlocks();
      runtimeResults.add(
          ToolCallResult.builder()
              .id(toolCall.id())
              .name(toolCall.name())
              .content(blocks == null ? result.content() : null)
              .contentBlocks(blocks == null ? List.of() : blocks)
              .completedAt(completedAt)
              .properties(result.properties())
              .build());
      for (final var update : result.contextUpdates()) {
        workingConversation = workingConversation.withContextUpdate(update);
      }
    }
    return new RuntimeToolCallSplit(workingConversation, runtimeResults, remainingCalls);
  }

  private RuntimeToolResult run(
      RuntimeTool tool,
      ToolCall toolCall,
      AgentExecutionContext executionContext,
      AgentConversation conversation) {
    final var documents = conversation.documentRegistry();
    final Map<String, Object> arguments =
        toolCall.arguments() == null ? Map.of() : toolCall.arguments();

    // a call of a tool without document parameters keeps its arguments, the other calls get the
    // ids of their documents
    final Map<String, Object> toolArguments;
    if (!DocumentParameterSchema.hasDocumentParameters(tool.definition().inputSchema())) {
      toolArguments = arguments;
    } else {
      switch (DocumentParameterResolver.resolveIds(toolCall, tool.definition(), documents)) {
        case DocumentParameterResolver.Resolution.Resolved resolved ->
            toolArguments = resolved.arguments();
        case DocumentParameterResolver.Resolution.Invalid invalid -> {
          return RuntimeToolResult.error(String.join(" ", invalid.problems()));
        }
      }
    }

    try {
      return tool.call(
          new RuntimeToolCall(toolCall.id(), toolCall.name(), toolArguments),
          executionContext,
          conversation);
    } catch (RuntimeToolException e) {
      LOGGER.debug("Runtime tool '{}' rejected the call '{}'", toolCall.name(), toolCall.id(), e);
      return RuntimeToolResult.error(e.getMessage());
    }
  }

  /**
   * Returns the error result for a call of a process tool whose document parameter is not the id of
   * a known document, or {@code null} if the call is valid or the tool declares no document
   * parameter.
   */
  private static @Nullable RuntimeToolResult validateDocumentParameters(
      ToolCall toolCall, AgentConversation conversation) {
    final var definition =
        conversation.configuration().toolDefinitions().stream()
            .filter(candidate -> candidate.name().equals(toolCall.name()))
            .filter(
                candidate -> DocumentParameterSchema.hasDocumentParameters(candidate.inputSchema()))
            .findFirst();
    if (definition.isEmpty()) {
      return null;
    }

    final var documents = conversation.documentRegistry();
    return switch (DocumentParameterResolver.resolve(toolCall, definition.get(), documents)) {
      case DocumentParameterResolver.Resolution.Resolved ignored -> null;
      case DocumentParameterResolver.Resolution.Invalid invalid ->
          RuntimeToolResult.error(String.join(" ", invalid.problems()));
    };
  }
}
