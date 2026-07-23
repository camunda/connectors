/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_TOOL_CALL_RESULTS_ON_EMPTY_CONTEXT;
import static io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent.objectContent;
import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContent.textContent;

import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentInput;
import io.camunda.connector.agenticai.aiagent.model.PreviousConversation;
import io.camunda.connector.agenticai.aiagent.model.message.DocumentReferenceXmlTag;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.MessageUtil;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.request.EventHandlingConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.api.error.ConnectorException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentConversationTurnInputComposerImpl implements AgentConversationTurnInputComposer {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(AgentConversationTurnInputComposerImpl.class);

  private static final String EVENT_CONTENT_EMPTY =
      "An event was triggered but no content was returned.";
  private static final String EVENT_CONTENT_EMPTY_INTERRUPT_TOOL_CALLS_EMPTY_MESSAGE =
      EVENT_CONTENT_EMPTY + " All in-flight tool executions were canceled.";
  private static final String EVENT_CONTENT_EMPTY_WAIT_FOR_TOOL_CALL_RESULTS_EMPTY_MESSAGE =
      EVENT_CONTENT_EMPTY
          + " Execution waited for all in-flight tool executions to complete before proceeding.";

  static final String EVENT_DOCUMENTS_PREAMBLE =
      "Documents extracted from event data (<doc /> tag + content pair):";

  private final GatewayToolHandlerRegistry gatewayToolHandlers;
  private final ToolCallResultDocumentExtractor documentExtractor;

  public AgentConversationTurnInputComposerImpl(GatewayToolHandlerRegistry gatewayToolHandlers) {
    this.gatewayToolHandlers = gatewayToolHandlers;
    this.documentExtractor = new ToolCallResultDocumentExtractor(gatewayToolHandlers);
  }

  @Override
  public CompositionResult compose(
      AgentConfiguration configuration,
      AgentContext agentContext,
      PreviousConversation previousConversation,
      AgentInput agentInput) {
    // tool call results arriving without a previous conversation is most likely a modeling error
    if (agentContext.conversation() == null && !agentInput.toolCallResults().isEmpty()) {
      throw new ConnectorException(
          ERROR_CODE_TOOL_CALL_RESULTS_ON_EMPTY_CONTEXT,
          "Agent received tool call results, but the agent context was empty (no previous conversation). Is the context configured correctly?");
    }

    boolean interruptToolCallsOnEventResults = interruptToolCallsOnEventResults(configuration);

    final List<Message> eventMessages =
        agentInput.eventMessages().stream()
            .map(eventResult -> createEventMessage(eventResult, interruptToolCallsOnEventResults))
            .toList();

    List<Message> messages = new ArrayList<>();

    boolean expectingToolCallResults =
        !previousConversation.turns().isEmpty()
            && previousConversation.turns().getLast().hasToolCalls();

    if (expectingToolCallResults) {
      boolean interruptMissingToolCalls =
          interruptToolCallsOnEventResults && !eventMessages.isEmpty();

      final var toolCalls = previousConversation.turns().getLast().toolCalls();

      final var orderedToolCallResults =
          resolveOrderedToolCallResults(
              agentContext, toolCalls, agentInput.toolCallResults(), interruptMissingToolCalls);

      // either we have all results or we interrupted the missing tool calls
      // if empty, we wait on further tool call results to be added
      if (orderedToolCallResults.isEmpty()) {
        return new CompositionResult.Deferred();
      }

      final var results = orderedToolCallResults.get();
      final var toolCallResultMessage =
          ToolCallResultMessage.builder()
              .results(toSelfDescribingContents(results))
              .metadata(MessageUtil.defaultMessageMetadata())
              .build();
      messages.add(toolCallResultMessage);
      messages.addAll(eventMessages);
    } else {
      messages.add(createUserPromptMessage(agentInput.userPrompt()));
      messages.addAll(eventMessages);
    }

    messages = messages.stream().filter(Objects::nonNull).toList();

    if (messages.isEmpty()) {
      LOGGER.debug("Not proceeding as no user content was found to add.");
      return new CompositionResult.NoInput();
    }

    return new CompositionResult.NextTurn(messages);
  }

  private @Nullable UserMessage createUserPromptMessage(AgentInput.UserPrompt userPrompt) {

    final var content = new ArrayList<Content>();

    // add user prompt text
    final var userPromptText = userPrompt.prompt();
    if (StringUtils.isNotBlank(userPromptText)) {
      content.add(textContent(userPromptText));
    }

    // add documents
    userPrompt.documents().stream().map(DocumentContent::documentContent).forEach(content::add);

    if (content.isEmpty()) {
      LOGGER.debug("Not adding user message as no user content was found to add.");
      return null;
    }

    return UserMessage.builder()
        .content(content)
        .metadata(MessageUtil.defaultMessageMetadata())
        .build();
  }

  /**
   * Resolves the ordered, raw {@link ToolCallResult} list for the current turn's tool call result
   * message — matching each of this turn's {@code toolCalls} to its result (or synthesizing a
   * cancelled result when interrupting), preserving the tool call order. Returns empty when results
   * are still incomplete and we are not interrupting, i.e. the caller should defer.
   *
   * <p>The raw list returned here feeds both the persisted {@link ToolCallResultMessage} (via
   * {@link ToolCallResultContent#from(ToolCallResult)}) and the unchanged document-extraction path
   * ({@link #createDocumentMessageForToolResults(List)}).
   */
  private Optional<List<ToolCallResult>> resolveOrderedToolCallResults(
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
                  ToolCallResult.forCancelledToolCall(
                      toolCall.id(), toolCall.name(), OffsetDateTime.now()));
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
      return Optional.empty();
    }

    return Optional.of(orderedToolCallResults);
  }

  /**
   * Lifts each raw {@link ToolCallResult}'s persisted {@link Content} shape (via {@link
   * ToolCallResultContent#from(ToolCallResult)}) plus any documents the gateway-aware {@link
   * #documentExtractor} extracts from the live, typed tool-return content, deduped by {@link
   * io.camunda.connector.api.document.DocumentReference} so a result whose raw content already
   * lifted to a bare {@link DocumentContent} is not double-added. Making the tool-call-result
   * self-describing this way is what lets {@code ToolCallResultStrategy} decide, per document, to
   * keep it inline or strip it into a synthetic fallback message without any further gateway
   * extraction at send-time.
   */
  private List<ToolCallResultContent> toSelfDescribingContents(List<ToolCallResult> results) {
    final var documentsByToolCallId =
        documentExtractor.extractDocuments(results).stream()
            .collect(
                Collectors.toMap(
                    ToolCallResultDocumentExtractor.ToolCallDocuments::toolCallId,
                    ToolCallResultDocumentExtractor.ToolCallDocuments::documents));

    return results.stream()
        .map(
            result -> {
              final var base = ToolCallResultContent.from(result);
              final var documents = documentsByToolCallId.getOrDefault(result.id(), List.of());
              if (documents.isEmpty()) {
                return base;
              }
              final var existingRefs =
                  base.content().stream()
                      .filter(DocumentContent.class::isInstance)
                      .map(c -> ((DocumentContent) c).document().reference())
                      .collect(Collectors.toCollection(HashSet::new));
              final var content = new ArrayList<Content>(base.content());
              for (var document : documents) {
                if (existingRefs.add(document.reference())) {
                  content.add(DocumentContent.documentContent(document));
                }
              }
              return base.withContent(content);
            })
        .toList();
  }

  private Message createEventMessage(
      ToolCallResult eventResult, boolean interruptToolCallsOnEventResults) {
    Object eventContent = eventResult.content();

    List<Content> userMessageContent = new ArrayList<>();
    if (eventContent == null || isEventContentEmpty(eventContent)) {
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

    // events arrive as ToolCallResult with null id/name — no tool-call attributes
    // on the rendered tag, but the extraction shape is otherwise the same
    final var eventDocuments = documentExtractor.extractDocuments(List.of(eventResult));
    if (!eventDocuments.isEmpty()) {
      userMessageContent.add(textContent(EVENT_DOCUMENTS_PREAMBLE));
      userMessageContent.addAll(createDocumentPairs(eventDocuments));
    }

    return UserMessage.builder()
        .content(userMessageContent)
        .metadata(MessageUtil.defaultMessageMetadata())
        .build();
  }

  private List<Content> createDocumentPairs(
      List<ToolCallResultDocumentExtractor.ToolCallDocuments> documentGroups) {
    final var content = new ArrayList<Content>();
    for (var group : documentGroups) {
      for (var doc : group.documents()) {
        content.add(
            textContent(
                DocumentReferenceXmlTag.from(doc, group.toolCallId(), group.toolCallName())
                    .toXml()));
        content.add(DocumentContent.documentContent(doc));
      }
    }
    return content;
  }

  private boolean isEventContentEmpty(@Nullable Object eventContent) {
    return switch (eventContent) {
      case null -> true;
      case String textContent -> StringUtils.isBlank(textContent);
      case Collection<?> collection -> collection.isEmpty();
      case Map<?, ?> map -> map.isEmpty();
      default -> false;
    };
  }

  private boolean interruptToolCallsOnEventResults(AgentConfiguration configuration) {
    final var behavior =
        Optional.ofNullable(configuration.events())
            .map(EventHandlingConfiguration::behavior)
            .orElse(null);

    return behavior == EventHandlingConfiguration.EventHandlingBehavior.INTERRUPT_TOOL_CALLS;
  }
}
