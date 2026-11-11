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

import io.camunda.connector.agenticai.aiagent.conversation.AgentInput;
import io.camunda.connector.agenticai.aiagent.conversation.AgentInput.UserPrompt;
import io.camunda.connector.agenticai.aiagent.conversation.PreviousConversation;
import io.camunda.connector.agenticai.aiagent.document.ObjectHandleRenderer;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocument;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentOrigin;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandling;
import io.camunda.connector.agenticai.aiagent.model.document.UnsupportedDocumentReferenceException;
import io.camunda.connector.agenticai.aiagent.model.message.MessageUtil;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.AgentDocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.request.EventHandlingConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.error.ConnectorException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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

  static final String TRACKED_EVENT_DOCUMENTS_PREAMBLE = "Documents attached to the event:";

  static final String UNTRACKED_EVENT_DOCUMENTS_PREAMBLE =
      "Documents extracted from event data (<doc /> tag + content pair):";

  private final GatewayToolHandlerRegistry gatewayToolHandlers;
  private final ToolCallResultDocumentExtractor documentExtractor;
  private final ToolCallResultsInputBuilder toolCallResultsInputBuilder;
  private final ObjectHandleRenderer objectHandleRenderer;

  public AgentConversationTurnInputComposerImpl(
      GatewayToolHandlerRegistry gatewayToolHandlers,
      ToolCallResultsInputBuilder toolCallResultsInputBuilder,
      ObjectHandleRenderer objectHandleRenderer) {
    this.gatewayToolHandlers = gatewayToolHandlers;
    this.documentExtractor = new ToolCallResultDocumentExtractor(gatewayToolHandlers);
    this.toolCallResultsInputBuilder = toolCallResultsInputBuilder;
    this.objectHandleRenderer = objectHandleRenderer;
  }

  @Override
  public CompositionResult resolve(
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
    final var documentHandling = agentContext.documentHandling();

    final var events =
        agentInput.eventMessages().stream()
            .map(eventResult ->
                eventInput(eventResult, interruptToolCallsOnEventResults, documentHandling))
            .reduce(TurnInput.empty(), TurnInput::followedBy);

    boolean expectingToolCallResults =
        !previousConversation.turns().isEmpty()
            && previousConversation.turns().getLast().hasToolCalls();

    if (expectingToolCallResults) {
      boolean interruptMissingToolCalls = interruptToolCallsOnEventResults && !events.isEmpty();

      final var previousTurn = previousConversation.turns().getLast();

      final var processResults =
          resolveProcessToolCallResults(
              agentContext,
              previousTurn.processToolCalls(),
              agentInput.toolCallResults(),
              interruptMissingToolCalls);
      final var processEntries =
          processResults.arrivedResults().stream()
              .map(result -> processEntry(result, documentHandling))
              .toList();
      switch (documentHandling) {
        case DocumentHandling.Tracked ignored ->
            processEntries.forEach(ToolCallBatch.Entry::requireCamundaDocuments);
        case DocumentHandling.Untracked ignored -> {}
      }

      // either we have all results or we interrupted the missing tool calls
      // if incomplete, we wait on further tool call results to be added
      if (!processResults.complete()) {
        return new CompositionResult.Deferred(processResults.arrivedResults());
      }

      var batch = ToolCallBatch.of(previousTurn.toolCalls()).withEntries(processEntries);
      if (interruptMissingToolCalls) {
        // every call without a result is cancelled: the missing process calls and the runtime
        // calls that were not executed yet; a rejected call keeps its rejection
        batch = batch.withEntries(batch.unanswered().stream().map(this::cancelled).toList());
      }
      return new CompositionResult.Ready(agentContext, batch, TurnInput.empty(), events);
    }

    final var userPrompt = userPromptInput(agentInput.userPrompt(), documentHandling);
    if (userPrompt.isEmpty() && events.isEmpty()) {
      LOGGER.debug("Not proceeding as no user content was found to add.");
      return new CompositionResult.NoInput();
    }
    return new CompositionResult.Ready(
        agentContext, ToolCallBatch.of(List.of()), userPrompt, events);
  }

  @Override
  public TurnInput assemble(CompositionResult.Ready ready) {
    final var input =
        ready.toolCallBatch().toolCalls().isEmpty()
            ? ready.userPrompt()
            : toolCallResultsInputBuilder.build(ready.agentContext(), ready.toolCallBatch());
    return input.followedBy(ready.events());
  }

  /** The user message of the user prompt and its documents, empty without user content. */
  private TurnInput userPromptInput(UserPrompt userPrompt, DocumentHandling documentHandling) {

    final var messageId = MessageUtil.generateId();
    final var content = new ArrayList<Content>();
    final var newDocuments = new ArrayList<AgentDocument>();

    // add user prompt text
    final var userPromptText = userPrompt.prompt();
    if (StringUtils.isNotBlank(userPromptText)) {
      content.add(textContent(userPromptText));
    }

    switch (documentHandling) {
      case DocumentHandling.Tracked ignored -> {
        // add the documents as blocks with the delivery content: the content is added when the
        // model is called
        userPrompt.documents().stream()
            .filter(doc -> !AgentDocument.isSupported(doc))
            .findFirst()
            .ifPresent(doc -> {
              throw UnsupportedDocumentReferenceException.inUserPrompt(doc);
            });
        final var origin = AgentDocumentOrigin.userMessage(messageId);
        for (var doc : userPrompt.documents()) {
          content.add(
              AgentDocumentContent.of(AgentDocumentId.of(doc))
                  .withDelivery(DocumentDelivery.CONTENT));
          newDocuments.add(AgentDocument.of(doc, origin));
        }
      }
      case DocumentHandling.Untracked ignored ->
          userPrompt.documents().stream()
              .map(DocumentContent::documentContent)
              .forEach(content::add);
    }

    if (content.isEmpty()) {
      LOGGER.debug("Not adding user message as no user content was found to add.");
      return TurnInput.empty();
    }
    return new TurnInput(
        List.of(
            UserMessage.builder()
                .id(messageId)
                .content(content)
                .metadata(MessageMetadata.defaults())
                .build()),
        newDocuments);
  }

  /**
   * @param arrivedResults the process results that arrived (correlated and gateway-transformed), in
   *     tool-call order
   * @param complete whether every process tool call has a result, or the missing ones are cancelled
   *     via interrupt
   */
  private record ProcessToolCallResults(List<ToolCallResult> arrivedResults, boolean complete) {}

  private ProcessToolCallResults resolveProcessToolCallResults(
      AgentContext agentContext,
      List<ToolCall> processToolCalls,
      List<ToolCallResult> toolCallResults,
      boolean interruptMissingToolCalls) {
    final var transformedToolCallResults =
        gatewayToolHandlers.transformToolCallResults(agentContext, toolCallResults);
    final var toolCallResultsById =
        transformedToolCallResults.stream()
            .collect(Collectors.toMap(ToolCallResult::id, Function.identity()));

    final var missingToolCalls = new ArrayList<ToolCall>();
    final var arrivedToolCallResults = new ArrayList<ToolCallResult>();
    processToolCalls.forEach(toolCall -> {
      final var result = toolCallResultsById.get(toolCall.id());
      if (result != null) {
        arrivedToolCallResults.add(result);
      } else {
        missingToolCalls.add(toolCall);
      }
    });

    // some results still missing, not interrupting due to events -> we wait for more to arrive
    if (!missingToolCalls.isEmpty() && !interruptMissingToolCalls) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(
            "Not adding tool call result message as tool call IDs {} were missing in tool call results.",
            missingToolCalls.stream().map(ToolCall::id).toList());
      }
      return new ProcessToolCallResults(arrivedToolCallResults, false);
    }
    return new ProcessToolCallResults(arrivedToolCallResults, true);
  }

  /**
   * The entry of a process tool result. An agent instance that tracks its documents registers the
   * documents the model reads as handles; an instance that does not adds the documents its gateway
   * tool handler extracts in a message of their own.
   */
  private ToolCallBatch.Entry processEntry(
      ToolCallResult result, DocumentHandling documentHandling) {
    return switch (documentHandling) {
      case DocumentHandling.Tracked ignored ->
          ToolCallBatch.Entry.withRenderedDocuments(
              ToolCallResultContent.from(result), objectHandleRenderer);
      case DocumentHandling.Untracked ignored ->
          ToolCallBatch.Entry.from(result, documentExtractor);
    };
  }

  private ToolCallBatch.Entry cancelled(ToolCall toolCall) {
    LOGGER.debug("Missing tool call result for ID: {}. Marking as canceled.", toolCall.id());
    return ToolCallBatch.Entry.from(
        ToolCallResult.forCancelledToolCall(
            toolCall.id(), toolCall.name(), OffsetDateTime.now(ZoneId.systemDefault())),
        documentExtractor);
  }

  /** The user message of an event and the documents of the event. */
  private TurnInput eventInput(
      ToolCallResult eventResult,
      boolean interruptToolCallsOnEventResults,
      DocumentHandling documentHandling) {
    Object eventContent = eventResult.content();
    final var messageId = MessageUtil.generateId();
    final var newDocuments = new ArrayList<AgentDocument>();

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

    // events arrive as ToolCallResult with null id/name, so their documents have no tool call
    switch (documentHandling) {
      case DocumentHandling.Tracked ignored -> {
        // the documents the model reads as handles
        final var eventDocuments =
            eventContent == null
                ? List.<Document>of()
                : ToolCallResultDocumentExtractor.renderedDocumentsIn(
                    eventContent, objectHandleRenderer);
        if (!eventDocuments.isEmpty()) {
          eventDocuments.stream()
              .filter(doc -> !AgentDocument.isSupported(doc))
              .findFirst()
              .ifPresent(doc -> {
                throw UnsupportedDocumentReferenceException.inEvent(doc);
              });
          userMessageContent.add(textContent(TRACKED_EVENT_DOCUMENTS_PREAMBLE));
          final var origin = AgentDocumentOrigin.event(messageId);
          for (var doc : eventDocuments) {
            newDocuments.add(AgentDocument.of(doc, origin));
            userMessageContent.add(AgentDocumentContent.of(AgentDocumentId.of(doc)));
          }
        }
      }
      case DocumentHandling.Untracked ignored -> {
        final var eventDocuments = documentExtractor.extractDocuments(List.of(eventResult));
        if (!eventDocuments.isEmpty()) {
          userMessageContent.add(textContent(UNTRACKED_EVENT_DOCUMENTS_PREAMBLE));
          userMessageContent.addAll(ToolCallResultsInputBuilder.documentPairs(eventDocuments));
        }
      }
    }

    return new TurnInput(
        List.of(
            UserMessage.builder()
                .id(messageId)
                .content(userMessageContent)
                .metadata(MessageMetadata.defaults())
                .build()),
        newDocuments);
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
