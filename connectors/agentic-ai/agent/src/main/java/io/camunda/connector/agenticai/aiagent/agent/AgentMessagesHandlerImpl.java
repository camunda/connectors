/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_TOOL_CALL_RESULTS_ON_EMPTY_CONTEXT;
import static io.camunda.connector.agenticai.model.message.MessageUtil.singleTextContent;
import static io.camunda.connector.agenticai.model.message.content.ObjectContent.objectContent;
import static io.camunda.connector.agenticai.model.message.content.TextContent.textContent;

import io.camunda.connector.agenticai.aiagent.memory.runtime.RuntimeMemory;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.EventHandlingConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.systemprompt.SystemPromptComposer;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.message.SystemMessage;
import io.camunda.connector.agenticai.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.model.message.UserMessage;
import io.camunda.connector.agenticai.model.message.content.Content;
import io.camunda.connector.agenticai.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.api.error.ConnectorException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentMessagesHandlerImpl implements AgentMessagesHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(AgentMessagesHandlerImpl.class);

  private static final String EVENT_CONTENT_EMPTY =
      "An event was triggered but no content was returned.";
  private static final String EVENT_CONTENT_EMPTY_INTERRUPT_TOOL_CALLS_EMPTY_MESSAGE =
      EVENT_CONTENT_EMPTY + " All in-flight tool executions were canceled.";
  private static final String EVENT_CONTENT_EMPTY_WAIT_FOR_TOOL_CALL_RESULTS_EMPTY_MESSAGE =
      EVENT_CONTENT_EMPTY
          + " Execution waited for all in-flight tool executions to complete before proceeding.";

  private final GatewayToolHandlerRegistry gatewayToolHandlers;
  private final SystemPromptComposer systemPromptComposer;

  public AgentMessagesHandlerImpl(
      GatewayToolHandlerRegistry gatewayToolHandlers, SystemPromptComposer systemPromptComposer) {
    this.gatewayToolHandlers = gatewayToolHandlers;
    this.systemPromptComposer = systemPromptComposer;
  }

  @Override
  public void addSystemMessage(
      AgentExecutionContext executionContext,
      AgentContext agentContext,
      RuntimeMemory memory,
      SystemPromptConfiguration systemPrompt) {
    final var composedSystemPrompt =
        systemPromptComposer.composeSystemPrompt(executionContext, agentContext, systemPrompt);

    if (StringUtils.isNotBlank(composedSystemPrompt)) {
      // memory will take care of replacing any existing system message if already present
      memory.addMessage(
          SystemMessage.builder().content(singleTextContent(composedSystemPrompt)).build());
    }
  }

  @Override
  public List<Message> addUserMessages(
      AgentExecutionContext executionContext,
      AgentContext agentContext,
      RuntimeMemory memory,
      UserPromptConfiguration userPrompt,
      List<ToolCallResult> toolCallResults) {
    boolean interruptToolCallsOnEventResults = interruptToolCallsOnEventResults(executionContext);

    // partitioned into 2 buckets - true -> with tool call ID, false -> without (from an event)
    final var partitionedByToolCallId =
        toolCallResults.stream().collect(Collectors.partitioningBy(result -> result.id() != null));
    final List<ToolCallResult> actualToolCallResults = partitionedByToolCallId.get(true);
    final List<Message> eventMessages =
        partitionedByToolCallId.get(false).stream()
            .map(eventResult -> createEventMessage(eventResult, interruptToolCallsOnEventResults))
            .toList();

    // throw an error when receiving tool call results on an empty context as
    // most likely this is a modeling error
    if (agentContext.conversation() == null && !actualToolCallResults.isEmpty()) {
      throw new ConnectorException(
          ERROR_CODE_TOOL_CALL_RESULTS_ON_EMPTY_CONTEXT,
          "Agent received tool call results, but the agent context was empty (no previous conversation). Is the context configured correctly?");
    }

    final var lastChatMessage = memory.lastMessage().orElse(null);

    List<Message> messages = new ArrayList<>();
    if (lastChatMessage instanceof AssistantMessage assistantMessage
        && assistantMessage.hasToolCalls()) {
      boolean interruptMissingToolCalls =
          interruptToolCallsOnEventResults && !eventMessages.isEmpty();

      ToolCallResultMessage toolCallResultMessage =
          createToolCallResultMessage(
              agentContext,
              assistantMessage.toolCalls(),
              actualToolCallResults,
              interruptMissingToolCalls);

      // either we have all results or we interrupted the missing tool calls
      // if message is null, we wait on further tool call results to be added
      if (toolCallResultMessage != null) {
        messages.add(toolCallResultMessage);
        messages.addAll(eventMessages);
      }
    } else {
      messages.add(createUserPromptMessage(userPrompt));
      messages.addAll(eventMessages);
    }

    messages = messages.stream().filter(Objects::nonNull).toList();
    messages.forEach(memory::addMessage);

    return messages;
  }

  private UserMessage createUserPromptMessage(UserPromptConfiguration userPrompt) {
    final var content = new ArrayList<Content>();

    // add user prompt text
    final var userPromptText = userPrompt.prompt();
    if (StringUtils.isNotBlank(userPromptText)) {
      content.add(textContent(userPromptText));
    }

    // add documents
    Optional.ofNullable(userPrompt.documents()).orElseGet(Collections::emptyList).stream()
        .map(DocumentContent::documentContent)
        .forEach(content::add);

    if (content.isEmpty()) {
      LOGGER.debug("Not adding user message as no user content was found to add.");
      return null;
    }

    return UserMessage.builder().content(content).metadata(defaultMessageMetadata()).build();
  }

  private ToolCallResultMessage createToolCallResultMessage(
      AgentContext agentContext,
      List<ToolCall> toolCalls,
      List<ToolCallResult> toolCallResults,
      boolean interruptMissingToolCalls) {
    final var transformedToolCallResults =
        gatewayToolHandlers.transformToolCallResults(agentContext, toolCallResults);
    final var toolCallResultsById =
        transformedToolCallResults.stream()
            .collect(Collectors.toMap(ToolCallResult::id, Function.identity()));

    final var missingToolCalls = new ArrayList<ToolCall>();
    final var orderedToolCallResults = new ArrayList<ToolCallResult>();
    toolCalls.forEach(
        toolCall -> {
          final var result = toolCallResultsById.get(toolCall.id());
          if (result != null) {
            orderedToolCallResults.add(result);
          } else {
            missingToolCalls.add(toolCall);
            if (interruptMissingToolCalls) {
              LOGGER.debug(
                  "Missing tool call result for ID: {}. Marking as canceled.", toolCall.id());
              orderedToolCallResults.add(
                  ToolCallResult.forCancelledToolCall(toolCall.id(), toolCall.name()));
            }
          }
        });

    // no results to return, not interrupting due to events -> we wait for more tool call results
    if (!missingToolCalls.isEmpty() && !interruptMissingToolCalls) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(
            "Not adding tool call result message as tool call IDs {} were missing in tool call results.",
            missingToolCalls.stream().map(ToolCall::id).toList());
      }
      return null;
    }

    return ToolCallResultMessage.builder()
        .results(orderedToolCallResults)
        .metadata(defaultMessageMetadata())
        .build();
  }

  private Message createEventMessage(
      ToolCallResult eventResult, boolean interruptToolCallsOnEventResults) {
    Object eventContent = eventResult.content();

    List<Content> userMessageContent = new ArrayList<>();
    if (isEventContentEmpty(eventContent)) {
      userMessageContent.add(
          textContent(
              interruptToolCallsOnEventResults
                  ? EVENT_CONTENT_EMPTY_INTERRUPT_TOOL_CALLS_EMPTY_MESSAGE
                  : EVENT_CONTENT_EMPTY_WAIT_FOR_TOOL_CALL_RESULTS_EMPTY_MESSAGE));
    } else {
      userMessageContent.add(
          switch (eventContent) {
            case String textContent -> textContent(textContent);
            default -> objectContent(eventContent);
          });
    }

    return UserMessage.builder()
        .content(userMessageContent)
        .metadata(defaultMessageMetadata())
        .build();
  }

  private boolean isEventContentEmpty(Object eventContent) {
    return switch (eventContent) {
      case null -> true;
      case String textContent -> StringUtils.isBlank(textContent);
      case Collection<?> collection -> collection.isEmpty();
      case Map<?, ?> map -> map.isEmpty();
      default -> false;
    };
  }

  private boolean interruptToolCallsOnEventResults(AgentExecutionContext executionContext) {
    final var behavior =
        Optional.ofNullable(executionContext.events())
            .map(EventHandlingConfiguration::behavior)
            .orElse(null);

    return behavior == EventHandlingConfiguration.EventHandlingBehavior.INTERRUPT_TOOL_CALLS;
  }

  private Map<String, Object> defaultMessageMetadata() {
    return Map.of("timestamp", ZonedDateTime.now());
  }
}
