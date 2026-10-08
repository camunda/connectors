/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.localtool.LocalTool;
import io.camunda.connector.agenticai.aiagent.localtool.LocalToolCall;
import io.camunda.connector.agenticai.aiagent.localtool.LocalToolContext;
import io.camunda.connector.agenticai.aiagent.localtool.LocalToolException;
import io.camunda.connector.agenticai.aiagent.localtool.LocalToolRegistry;
import io.camunda.connector.agenticai.aiagent.localtool.LocalToolResult;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentConversation;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameterSchema;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the calls of available {@link LocalTool}s of a batch and validates the document parameters
 * of the calls of process tools. A call that gets a local result is answered by the agent, every
 * other call remains for the process.
 */
public class LocalToolCallHandlerImpl implements LocalToolCallHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(LocalToolCallHandlerImpl.class);

  private final LocalToolRegistry localToolRegistry;
  private final ObjectMapper objectMapper;

  public LocalToolCallHandlerImpl(LocalToolRegistry localToolRegistry, ObjectMapper objectMapper) {
    this.localToolRegistry = localToolRegistry;
    this.objectMapper = objectMapper;
  }

  @Override
  public List<ToolDefinition> availableToolDefinitions(AgentConfiguration configuration) {
    return localToolRegistry.availableToolDefinitions(configuration);
  }

  @Override
  public LocalToolCallSplit handle(AgentConversation conversation, List<ToolCall> toolCalls) {
    // an agent instance without the documents feature has no local tools and no document
    // parameters: every call goes to the process as the model sent it
    if (toolCalls.isEmpty() || !conversation.supportsDocuments()) {
      return LocalToolCallSplit.allRemaining(conversation, toolCalls);
    }

    final var completedAt = OffsetDateTime.now();
    final var localResults = new ArrayList<ToolCallResult>();
    final var remainingCalls = new ArrayList<ToolCall>();
    var workingConversation = conversation;
    for (final var toolCall : toolCalls) {
      final var tool =
          localToolRegistry.findAvailable(toolCall.name(), workingConversation.configuration());
      final LocalToolResult result;
      if (tool.isPresent()) {
        result = run(tool.get(), toolCall, workingConversation);
      } else {
        final var rejection = validateDocumentParameters(toolCall, workingConversation);
        if (rejection == null) {
          remainingCalls.add(toolCall);
          continue;
        }
        result = rejection;
      }

      localResults.add(
          ToolCallResult.builder()
              .id(toolCall.id())
              .name(toolCall.name())
              .content(result.content())
              .completedAt(completedAt)
              .properties(result.properties())
              .build());
      for (final var update : result.contextUpdates()) {
        workingConversation = workingConversation.withContextUpdate(update);
      }
    }
    return new LocalToolCallSplit(workingConversation, localResults, remainingCalls);
  }

  private LocalToolResult run(LocalTool tool, ToolCall toolCall, AgentConversation conversation) {
    final var documents = conversation.documentRegistry();
    final Map<String, Object> arguments =
        toolCall.arguments() == null ? Map.of() : toolCall.arguments();

    final Map<String, Object> resolvedArguments;
    if (!DocumentParameterSchema.hasDocumentParameters(tool.definition().inputSchema())) {
      resolvedArguments = arguments;
    } else {
      switch (DocumentParameterResolver.resolve(toolCall, tool.definition(), documents)) {
        case DocumentParameterResolver.Resolution.Resolved resolved ->
            resolvedArguments = resolved.arguments();
        case DocumentParameterResolver.Resolution.Invalid invalid -> {
          return LocalToolResult.error(
              String.join(" ", invalid.problems()) + " " + documents.knownDocumentsText());
        }
      }
    }

    final var context =
        new LocalToolContext(
            conversation.configuration(),
            conversation.toAgentContext().properties(),
            documents,
            conversation.loadedDocumentIds(),
            conversation.agentInstanceKey(),
            objectMapper);
    try {
      return tool.call(
          new LocalToolCall(toolCall.id(), toolCall.name(), arguments, resolvedArguments), context);
    } catch (LocalToolException e) {
      LOGGER.debug("Local tool '{}' rejected the call '{}'", toolCall.name(), toolCall.id(), e);
      return LocalToolResult.error(e.getMessage());
    }
  }

  /**
   * Returns the error result for a call of a process tool whose document parameter is not the id of
   * a known document, or {@code null} if the call is valid or the tool declares no document
   * parameter.
   */
  private static @Nullable LocalToolResult validateDocumentParameters(
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
          LocalToolResult.error(
              String.join(" ", invalid.problems()) + " " + documents.knownDocumentsText());
    };
  }
}
