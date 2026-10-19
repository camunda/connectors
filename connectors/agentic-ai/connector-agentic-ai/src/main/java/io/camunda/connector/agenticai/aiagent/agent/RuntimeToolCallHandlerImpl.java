/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.agent.RuntimeToolCallAnswers.RuntimeToolCallAnswer;
import io.camunda.connector.agenticai.aiagent.document.DocumentParameterResolver;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentConversation;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameterSchema;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeTool;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolCall;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolConversation;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolException;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolRegistry;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolResult;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Splits the calls of a batch into the calls of available {@link RuntimeTool}s, the calls of
 * process tools whose document parameters are not valid, and the calls that remain for the process.
 * The first two kinds are answered by the agent.
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
  public RuntimeToolCallSplit split(
      AgentExecutionContext executionContext,
      AgentConversation conversation,
      List<ToolCall> toolCalls) {
    final var agentContext = conversation.toAgentContext();
    final var answeredCalls = new ArrayList<AnsweredCall>();
    final var remainingCalls = new ArrayList<ToolCall>();
    for (final var toolCall : toolCalls) {
      final var tool =
          runtimeToolRegistry.findAvailable(toolCall.name(), executionContext, agentContext);
      if (tool.isPresent()) {
        answeredCalls.add(new AnsweredCall(toolCall, tool.get(), null));
        continue;
      }

      // an agent instance without the documents feature has no document parameters
      final var rejection =
          conversation.supportsDocuments()
              ? validateDocumentParameters(toolCall, conversation)
              : null;
      if (rejection == null) {
        remainingCalls.add(toolCall);
      } else {
        answeredCalls.add(new AnsweredCall(toolCall, null, rejection));
      }
    }
    return new Split(executionContext, conversation, answeredCalls, remainingCalls);
  }

  /**
   * A call the agent answers itself: the call of a runtime tool, or a call of a process tool that
   * is answered with the rejection.
   */
  private record AnsweredCall(
      ToolCall toolCall, @Nullable RuntimeTool tool, @Nullable RuntimeToolResult rejection) {}

  private final class Split implements RuntimeToolCallSplit {

    private final AgentExecutionContext executionContext;
    private final AgentConversation conversation;
    private final List<AnsweredCall> answeredCalls;
    private final List<ToolCall> remainingCalls;

    private Split(
        AgentExecutionContext executionContext,
        AgentConversation conversation,
        List<AnsweredCall> answeredCalls,
        List<ToolCall> remainingCalls) {
      this.executionContext = executionContext;
      this.conversation = conversation;
      this.answeredCalls = List.copyOf(answeredCalls);
      this.remainingCalls = List.copyOf(remainingCalls);
    }

    @Override
    public List<ToolCall> remainingCalls() {
      return remainingCalls;
    }

    @Override
    public List<ToolCall> answeredCalls() {
      return answeredCalls.stream().map(AnsweredCall::toolCall).toList();
    }

    @Override
    public RuntimeToolCallAnswers answer() {
      final var completedAt = OffsetDateTime.now();
      final var answers = new ArrayList<RuntimeToolCallAnswer>();
      final var contextUpdates = new ArrayList<UnaryOperator<AgentContext>>();
      var workingConversation = conversation;
      for (final var answered : answeredCalls) {
        final var toolCall = answered.toolCall();
        final var result =
            answered.rejection() != null
                ? answered.rejection()
                : run(
                    Objects.requireNonNull(answered.tool()),
                    toolCall,
                    executionContext,
                    workingConversation);

        final var blocks = result.contentBlocks();
        answers.add(
            new RuntimeToolCallAnswer(
                ToolCallResultContent.builder()
                    .id(toolCall.id())
                    .name(toolCall.name())
                    .content(
                        blocks == null
                            ? ToolCallResultContent.contentFromObject(result.content())
                            : blocks)
                    .completedAt(completedAt)
                    .properties(result.properties())
                    .build(),
                result.documentIdsWithContent()));
        for (final var update : result.contextUpdates()) {
          contextUpdates.add(update);
          workingConversation = workingConversation.withContextUpdate(update);
        }
      }
      return new RuntimeToolCallAnswers(answers, contextUpdates);
    }
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
          new RuntimeToolConversation(
              conversation.toAgentContext(), conversation.windowedMessages()));
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
