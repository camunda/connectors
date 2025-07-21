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
import static io.camunda.connector.agenticai.model.tool.ToolCallResult.PROPERTY_INTERRUPTED;
import static io.camunda.connector.agenticai.util.PromptUtils.resolveParameterizedPrompt;

import io.camunda.connector.agenticai.aiagent.memory.runtime.RuntimeMemory;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.EventHandlingConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
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

  private final GatewayToolHandlerRegistry gatewayToolHandlers;

  public AgentMessagesHandlerImpl(GatewayToolHandlerRegistry gatewayToolHandlers) {
    this.gatewayToolHandlers = gatewayToolHandlers;
  }

  @Override
  public void addSystemMessage(
      AgentExecutionContext executionContext,
      AgentContext agentContext,
      RuntimeMemory memory,
      SystemPromptConfiguration systemPrompt) {
    final var systemPromptText =
        resolveParameterizedPrompt(systemPrompt.prompt(), systemPrompt.parameters());
    if (StringUtils.isNotBlank(systemPromptText)) {
      // memory will take care of replacing any existing system message if already present
      memory.addMessage(
          SystemMessage.builder().content(singleTextContent(systemPromptText)).build());
    }
  }

  @Override
  public List<Message> addUserMessages(
      AgentExecutionContext executionContext,
      AgentContext agentContext,
      RuntimeMemory memory,
      UserPromptConfiguration userPrompt,
      List<ToolCallResult> toolCallResults) {

    final var partitionedByToolCallId =
        toolCallResults.stream().collect(Collectors.partitioningBy(result -> result.id() != null));
    final List<ToolCallResult> actualToolCallResults = partitionedByToolCallId.get(true);
    final List<Message> eventMessages =
        partitionedByToolCallId.get(false).stream()
            .map(this::createEventMessage)
            .filter(Objects::nonNull)
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
      boolean interruptToolCallsOnEventResults = interruptToolCallsOnEventResults(executionContext);
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
    final var userPromptText =
        resolveParameterizedPrompt(userPrompt.prompt(), userPrompt.parameters());
    if (StringUtils.isNotBlank(userPromptText)) {
      content.add(textContent(userPromptText));
    }

    // add documents
    Optional.ofNullable(userPrompt.documents()).orElseGet(Collections::emptyList).stream()
        .map(DocumentContent::new)
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
                  ToolCallResult.builder()
                      .id(toolCall.id())
                      .name(toolCall.name())
                      .content("Tool execution was canceled.")
                      .properties(Map.of(PROPERTY_INTERRUPTED, true))
                      .build());
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

  private Message createEventMessage(ToolCallResult eventResult) {
    if (eventResult.content() == null) {
      return null;
    }

    Content contentBlock = null;
    if (eventResult.content() instanceof String textContent) {
      if (StringUtils.isNotBlank(textContent)) {
        contentBlock = textContent(textContent);
      }
    } else {
      contentBlock = objectContent(eventResult.content());
    }

    if (contentBlock == null) {
      return null;
    }

    return UserMessage.builder()
        .content(List.of(contentBlock))
        .metadata(defaultMessageMetadata())
        .build();
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
