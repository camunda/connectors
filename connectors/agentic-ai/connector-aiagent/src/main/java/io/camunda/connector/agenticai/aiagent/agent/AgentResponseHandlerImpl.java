/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_FAILED_TO_PARSE_RESPONSE_CONTENT;
import static io.camunda.connector.agenticai.aiagent.util.ResponseTextUtil.stripMarkdownCodeBlocks;
import static io.camunda.connector.agenticai.common.json.JacksonExceptionMessageExtractor.humanReadableJsonProcessingExceptionMessage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.conversation.AgentConversation;
import io.camunda.connector.agenticai.aiagent.conversation.AgentConversationTurn;
import io.camunda.connector.agenticai.aiagent.document.DocumentParameterResolver;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandling;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.JsonResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.TextResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.request.AgentTaskResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.aiagent.tool.ToolCallProcessVariable;
import io.camunda.connector.api.error.ConnectorException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentResponseHandlerImpl implements AgentResponseHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(AgentResponseHandlerImpl.class);
  private static final ResponseConfiguration DEFAULT_RESPONSE_CONFIGURATION =
      new AgentTaskResponseConfiguration(new TextResponseFormatConfiguration(false), false);

  private final ObjectMapper objectMapper;
  private final GatewayToolHandlerRegistry gatewayToolHandlers;
  private final DocumentParameterResolver documentParameterResolver;

  public AgentResponseHandlerImpl(
      ObjectMapper objectMapper,
      GatewayToolHandlerRegistry gatewayToolHandlers,
      DocumentParameterResolver documentParameterResolver) {
    this.objectMapper = objectMapper;
    this.gatewayToolHandlers = gatewayToolHandlers;
    this.documentParameterResolver = documentParameterResolver;
  }

  @Override
  public AgentResponse createResponse(AgentConversation conversation) {
    final var agentContext = conversation.toAgentContext();
    final var lastTurn = lastTurnOf(conversation);
    final var assistantMessage = assistantMessageOf(lastTurn);
    // the stored assistant message keeps every tool call as the model sent it; only the calls
    // handed to the process become tool calls of the response, and these carry the Camunda
    // document references in place of the document handles
    final var toolCalls =
        gatewayToolHandlers
            .transformToolCalls(
                agentContext, withDocumentReferences(agentContext, lastTurn.processToolCalls()))
            .stream()
            .map(ToolCallProcessVariable::from)
            .toList();

    // default to text content only if not configured
    final var responseConfiguration =
        Optional.ofNullable(conversation.configuration().response())
            .orElse(DEFAULT_RESPONSE_CONFIGURATION);

    final var builder = AgentResponse.builder().context(agentContext).toolCalls(toolCalls);
    if (toolCalls.isEmpty()) {
      switch (agentContext.documentHandling()) {
        // every final response carries all response documents, which stay in the context for the
        // lifetime of the agent instance
        case DocumentHandling.Tracked ignored ->
            builder.responseDocuments(responseDocuments(agentContext));
        // an instance that does not track its documents has no response documents
        case DocumentHandling.Untracked ignored -> {}
      }
    }
    if (Boolean.TRUE.equals(responseConfiguration.includeAssistantMessage())) {
      builder.responseMessage(assistantMessage);
    }

    deriveResponseText(assistantMessage)
        .ifPresent(
            responseText -> handleFirstResponseText(responseConfiguration, builder, responseText));

    return builder.build();
  }

  /**
   * The calls with the documents of their document parameters resolved, with the tool definitions
   * of the agent context. The calls that go to the process were admitted against these definitions
   * (see {@link ToolCallAdmission}): each has a definition, and a call with an invalid document
   * parameter is rejected (see {@link ToolCall#rejection()}).
   */
  private List<ToolCall> withDocumentReferences(
      AgentContext agentContext, List<ToolCall> processToolCalls) {
    return processToolCalls.stream()
        .map(toolCall ->
            toolCall.withArguments(
                documentParameterResolver.resolveArguments(
                    agentContext, definitionOf(agentContext, toolCall), toolCall)))
        .toList();
  }

  private static ToolDefinition definitionOf(AgentContext agentContext, ToolCall toolCall) {
    return agentContext
        .toolDefinition(toolCall.name())
        .orElseThrow(() ->
            new IllegalStateException(
                "The call '%s' of the tool '%s' goes to the process, but the agent context has no tool of this name"
                    .formatted(toolCall.id(), toolCall.name())));
  }

  private static AgentConversationTurn lastTurnOf(AgentConversation conversation) {
    return conversation
        .lastTurn()
        .orElseThrow(() ->
            new IllegalStateException(
                "Cannot create an agent response: the conversation has no completed turn"));
  }

  private static AssistantMessage assistantMessageOf(AgentConversationTurn lastTurn) {
    return Objects.requireNonNull(
        lastTurn.assistantMessage(),
        "Cannot create an agent response: the last turn has no assistant message");
  }

  private Map<String, ReturnedDocument> responseDocuments(AgentContext agentContext) {
    final var returned = new LinkedHashMap<String, ReturnedDocument>();
    for (final var selection : agentContext.responseDocuments()) {
      final var document =
          agentContext
              .documents()
              .find(selection.document())
              .orElseThrow(() ->
                  new IllegalStateException(
                      "The response document '%s' is the document '%s', which the agent does not know"
                          .formatted(selection.key(), selection.document())));
      returned.put(
          selection.key(),
          new ReturnedDocument(
              documentParameterResolver.referenceJson(document), selection.description()));
    }
    return returned;
  }

  private Optional<String> deriveResponseText(AssistantMessage assistantMessage) {
    return Optional.of(
            assistantMessage.content().stream()
                .filter(c -> c instanceof TextContent)
                .map(c -> ((TextContent) c).text())
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining()))
        .filter(StringUtils::isNotBlank);
  }

  private void handleFirstResponseText(
      ResponseConfiguration responseConfiguration,
      AgentResponseBuilder responseBuilder,
      String responseText) {

    if (StringUtils.isBlank(responseText)) {
      return;
    }

    // keep null handling for backward compatibility
    final var format =
        Optional.ofNullable(responseConfiguration.format())
            .orElseGet(DEFAULT_RESPONSE_CONFIGURATION::format);

    switch (format) {
      case TextResponseFormatConfiguration textFormat ->
          handleTextResponseFormat(responseBuilder, responseText, textFormat);
      case JsonResponseFormatConfiguration ignored ->
          handleJsonResponseFormat(responseBuilder, responseText);
    }
  }

  private void handleTextResponseFormat(
      AgentResponseBuilder responseBuilder,
      String responseText,
      TextResponseFormatConfiguration textFormat) {
    responseBuilder.responseText(responseText);

    if (Boolean.TRUE.equals(textFormat.parseJson())) {
      try {
        parseTextToResponseJson(responseBuilder, responseText);
      } catch (Exception e) {
        var message = e.getMessage();
        if (e instanceof JsonProcessingException jpe) {
          message = humanReadableJsonProcessingExceptionMessage(jpe);
        }

        LOGGER.warn("Failed to parse response content as JSON: {}", message);
      }
    }
  }

  private void handleJsonResponseFormat(AgentResponseBuilder responseBuilder, String responseText) {
    try {
      parseTextToResponseJson(responseBuilder, responseText);
    } catch (JsonProcessingException e) {
      throw new ConnectorException(
          ERROR_CODE_FAILED_TO_PARSE_RESPONSE_CONTENT,
          "Failed to parse response content as JSON: %s"
              .formatted(humanReadableJsonProcessingExceptionMessage(e)));
    }
  }

  private void parseTextToResponseJson(AgentResponseBuilder responseBuilder, String responseText)
      throws JsonProcessingException {
    // Strip markdown code blocks before parsing to handle AI models (like Anthropic Claude)
    // that wrap JSON responses in ```json ... ``` code fences
    String cleanedText = Objects.requireNonNull(stripMarkdownCodeBlocks(responseText));
    Object responseJson = objectMapper.readValue(cleanedText, Object.class);
    responseBuilder.responseJson(responseJson);
  }
}
