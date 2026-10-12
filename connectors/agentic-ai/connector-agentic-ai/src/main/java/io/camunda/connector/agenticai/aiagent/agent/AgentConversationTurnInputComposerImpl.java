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

import io.camunda.connector.agenticai.aiagent.localtool.DocumentLoadGuard;
import io.camunda.connector.agenticai.aiagent.localtool.DocumentLoadGuard.Rejection;
import io.camunda.connector.agenticai.aiagent.localtool.GetDocumentTool;
import io.camunda.connector.agenticai.aiagent.memory.runtime.MessageWindowFilter;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentConversation;
import io.camunda.connector.agenticai.aiagent.model.AgentInput;
import io.camunda.connector.agenticai.aiagent.model.PreviousConversation;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.model.document.IngestedDocument;
import io.camunda.connector.agenticai.aiagent.model.message.DocumentReferenceXmlTag;
import io.camunda.connector.agenticai.aiagent.model.message.LegacyDocumentReferenceXmlTag;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.MessageId;
import io.camunda.connector.agenticai.aiagent.model.message.MessageUtil;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.request.EventHandlingConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.error.ConnectorException;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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

  // the preambles of the tag and content pairs of an agent instance without the documents feature
  static final String TOOL_CALL_DOCUMENTS_PREAMBLE =
      "Documents extracted from tool calls (<doc /> tag + content pair):";
  static final String EVENT_DOCUMENTS_PREAMBLE =
      "Documents extracted from event data (<doc /> tag + content pair):";

  static final String EVENT_DOCUMENT_TAGS_PREAMBLE = "Documents attached to the event:";
  static final String LOADED_DOCUMENTS_PREAMBLE = "Documents loaded for the tool results:";

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
    final boolean documentFlow = agentContext.supportsDocuments();

    // events are composed first but follow the prompt or tool call results in the turn
    final var eventDocuments = new ArrayList<IngestedDocument>();
    final var ingestedDocuments = new ArrayList<IngestedDocument>();

    final List<Message> eventMessages =
        agentInput.eventMessages().stream()
            .map(
                eventResult ->
                    createEventMessage(
                        eventResult,
                        interruptToolCallsOnEventResults,
                        documentFlow,
                        eventDocuments))
            .toList();

    List<Message> messages = new ArrayList<>();

    boolean expectingToolCallResults =
        !previousConversation.turns().isEmpty()
            && previousConversation.turns().getLast().hasToolCalls();

    if (expectingToolCallResults) {
      boolean interruptMissingToolCalls =
          interruptToolCallsOnEventResults && !eventMessages.isEmpty();

      final var toolCalls = previousConversation.turns().getLast().toolCalls();

      final var resolution =
          resolveOrderedToolCallResults(
              agentContext, toolCalls, agentInput.toolCallResults(), interruptMissingToolCalls);

      // either we have all results or we interrupted the missing tool calls
      // if incomplete, we wait on further tool call results to be added
      if (!resolution.complete()) {
        return new CompositionResult.Deferred(resolution.arrivedResults());
      }

      final var toolCallResults = resolution.results();
      final var toolCallResultMessage =
          ToolCallResultMessage.builder()
              .results(toolCallResults.stream().map(ToolCallResultContent::from).toList())
              .metadata(defaultMessageMetadata())
              .build();
      messages.add(toolCallResultMessage);
      if (documentFlow) {
        // the documents of the results are shown as their tags inside the tool results
        ingestToolCallResultDocuments(
            toolCallResults, toolCallResultMessage.id(), ingestedDocuments);
        final var loadedDocumentsMessage =
            composeLoadedDocumentsMessage(
                getDocumentTags(DocumentRegistry.of(agentContext.documents()), toolCallResults),
                inlineToolDocumentContent(
                    agentContext,
                    configuration,
                    previousConversation,
                    toolCallResults,
                    toolCallResultMessage.id()));
        if (loadedDocumentsMessage != null) {
          messages.add(loadedDocumentsMessage);
        }
      } else {
        final var documentMessage = createLegacyDocumentMessageForToolResults(toolCallResults);
        if (documentMessage != null) {
          messages.add(documentMessage);
        }
      }
      messages.addAll(eventMessages);
    } else {
      messages.add(
          createUserPromptMessage(
              agentInput.userPrompt(),
              documentFlow,
              documentFlow && agentInput.userPrompt().loadDocuments()
                  ? loadedDocumentIds(configuration, previousConversation)
                  : Set.of(),
              ingestedDocuments));
      messages.addAll(eventMessages);
    }

    messages = messages.stream().filter(Objects::nonNull).toList();

    if (messages.isEmpty()) {
      LOGGER.debug("Not proceeding as no user content was found to add.");
      return new CompositionResult.NoInput();
    }

    ingestedDocuments.addAll(eventDocuments);
    return new CompositionResult.NextTurn(messages, ingestedDocuments);
  }

  private @Nullable UserMessage createUserPromptMessage(
      AgentInput.UserPrompt userPrompt,
      boolean documentFlow,
      Set<String> alreadyLoadedDocumentIds,
      List<IngestedDocument> ingestedDocuments) {

    final var messageId = MessageUtil.generateId();
    final var content = new ArrayList<Content>();

    // add user prompt text
    final var userPromptText = userPrompt.prompt();
    if (StringUtils.isNotBlank(userPromptText)) {
      content.add(textContent(userPromptText));
    }

    final var source = DocumentSource.userMessage(messageId.toString());
    if (documentFlow) {
      // add documents as tags; the content is only added, from the registry when the model is
      // called, for the tags marked as loaded
      final var handledDocumentIds = new HashSet<>(alreadyLoadedDocumentIds);
      for (var doc : userPrompt.documents()) {
        final var tag = DocumentReferenceXmlTag.from(doc, source);
        if (!userPrompt.loadDocuments() || !handledDocumentIds.add(tag.id())) {
          content.add(tag.toContent());
          continue;
        }

        final var rejection = DocumentLoadGuard.check(doc).orElse(null);
        if (rejection == null) {
          content.add(tag.toLoadedContent());
        } else {
          content.add(tag.toContent());
          content.add(textContent(notLoadedNote(tag.id(), rejection)));
        }
      }
    } else {
      // an agent instance without the documents feature keeps the documents as content
      userPrompt.documents().stream().map(DocumentContent::documentContent).forEach(content::add);
    }

    if (content.isEmpty()) {
      LOGGER.debug("Not adding user message as no user content was found to add.");
      return null;
    }

    if (documentFlow) {
      userPrompt
          .documents()
          .forEach(doc -> ingestedDocuments.add(new IngestedDocument(doc, source)));
    }

    return UserMessage.builder()
        .id(messageId)
        .content(content)
        .metadata(defaultMessageMetadata())
        .build();
  }

  /**
   * @param results the currently resolved results in tool-call order: the results the agent
   *     answered itself, the correlated and gateway-transformed process results and, when
   *     interrupting, the cancelled ones; partial when {@code complete} is {@code false}
   * @param arrivedResults the process results among {@code results} (correlated and
   *     gateway-transformed), in tool-call order
   * @param complete whether every tool call has a result (or was cancelled via interrupt)
   */
  private record ToolCallResultsResolution(
      List<ToolCallResult> results, List<ToolCallResult> arrivedResults, boolean complete) {}

  private ToolCallResultsResolution resolveOrderedToolCallResults(
      AgentContext agentContext,
      List<ToolCall> toolCalls,
      List<ToolCallResult> toolCallResults,
      boolean interruptMissingToolCalls) {
    final var transformedToolCallResults =
        gatewayToolHandlers.transformToolCallResults(agentContext, toolCallResults);
    final var toolCallResultsById =
        transformedToolCallResults.stream()
            .collect(Collectors.toMap(ToolCallResult::id, Function.identity()));
    // answered by the agent when the tool calls were issued; never gateway-transformed
    final var localToolCallResultsById =
        agentContext.pendingLocalToolCallResults().stream()
            .collect(Collectors.toMap(ToolCallResult::id, Function.identity(), (a, b) -> a));

    final var missingToolCalls = new ArrayList<ToolCall>();
    final var orderedToolCallResults = new ArrayList<ToolCallResult>();
    final var arrivedToolCallResults = new ArrayList<ToolCallResult>();
    toolCalls.forEach(
        toolCall -> {
          final var localResult = localToolCallResultsById.get(toolCall.id());
          final var result = toolCallResultsById.get(toolCall.id());
          if (localResult != null) {
            orderedToolCallResults.add(localResult);
          } else if (result != null) {
            orderedToolCallResults.add(result);
            arrivedToolCallResults.add(result);
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

    // some results still missing, not interrupting due to events -> we wait for more to arrive
    if (!missingToolCalls.isEmpty() && !interruptMissingToolCalls) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(
            "Not adding tool call result message as tool call IDs {} were missing in tool call results.",
            missingToolCalls.stream().map(ToolCall::id).toList());
      }
      return new ToolCallResultsResolution(orderedToolCallResults, arrivedToolCallResults, false);
    }

    return new ToolCallResultsResolution(orderedToolCallResults, arrivedToolCallResults, true);
  }

  @Override
  public @Nullable UserMessage composeLoadedDocumentsMessage(
      DocumentRegistry registry, List<ToolCallResult> results) {
    return composeLoadedDocumentsMessage(getDocumentTags(registry, results), List.of());
  }

  /** The tags, marked as loaded, of the documents that the results of c8__get_document loaded. */
  private List<Content> getDocumentTags(DocumentRegistry registry, List<ToolCallResult> results) {
    return results.stream()
        .filter(result -> GetDocumentTool.NAME.equals(result.name()))
        .map(result -> result.properties().get(ToolCallResult.PROPERTY_LOADED_DOCUMENT_ID))
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .distinct()
        .map(registry::findById)
        .flatMap(Optional::stream)
        .<Content>map(entry -> DocumentReferenceXmlTag.from(entry).toLoadedContent())
        .toList();
  }

  /**
   * The content for the documents of the results of tools with inline document delivery: the tag,
   * marked as loaded, of every document that passes the load guard, and a note for every document
   * that does not. A document that is already loaded in the conversation, or loaded by another
   * result of the turn, is left out.
   */
  private List<Content> inlineToolDocumentContent(
      AgentContext agentContext,
      AgentConfiguration configuration,
      PreviousConversation previousConversation,
      List<ToolCallResult> results,
      MessageId toolCallResultMessageId) {
    final var inlineToolNames =
        agentContext.toolDefinitions().stream()
            .filter(tool -> tool.documentDelivery() == DocumentDelivery.INLINE)
            .map(ToolDefinition::name)
            .collect(Collectors.toSet());
    if (inlineToolNames.isEmpty()) {
      return List.of();
    }

    final var handledDocumentIds =
        new HashSet<>(loadedDocumentIds(configuration, previousConversation));
    results.stream()
        .filter(result -> GetDocumentTool.NAME.equals(result.name()))
        .map(result -> result.properties().get(ToolCallResult.PROPERTY_LOADED_DOCUMENT_ID))
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .forEach(handledDocumentIds::add);

    final var content = new ArrayList<Content>();
    for (var group : documentExtractor.extractDocuments(results)) {
      if (!inlineToolNames.contains(group.toolCallName())) {
        continue;
      }
      final var source =
          DocumentSource.toolCall(
              group.toolCallName(), group.toolCallId(), toolCallResultMessageId.toString());
      for (Document doc : group.documents()) {
        final var id = DocumentHandle.idFor(doc);
        if (!handledDocumentIds.add(id)) {
          continue;
        }

        final var rejection = DocumentLoadGuard.check(doc).orElse(null);
        content.add(
            rejection == null
                ? DocumentReferenceXmlTag.from(doc, source).toLoadedContent()
                : textContent(notLoadedNote(id, rejection)));
      }
    }
    return content;
  }

  /**
   * The message that follows the tool results of a turn and carries the documents loaded for them:
   * the tags marked as loaded, after the preamble, then the notes of the documents that were not
   * loaded.
   */
  private @Nullable UserMessage composeLoadedDocumentsMessage(
      List<Content> loadedTags, List<Content> inlineContent) {
    final var loadedContent = new ArrayList<Content>(loadedTags);
    loadedContent.addAll(inlineContent);
    if (loadedContent.isEmpty()) {
      return null;
    }

    final var content = new ArrayList<Content>();
    if (loadedContent.stream().anyMatch(AgentConversationTurnInputComposerImpl::isLoadedTag)) {
      content.add(textContent(LOADED_DOCUMENTS_PREAMBLE));
    }
    // the notes follow the tags
    loadedContent.stream()
        .filter(AgentConversationTurnInputComposerImpl::isLoadedTag)
        .forEach(content::add);
    loadedContent.stream().filter(tag -> !isLoadedTag(tag)).forEach(content::add);

    final var metadata = new HashMap<>(defaultMessageMetadata());
    metadata.put(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true);
    return UserMessage.builder().content(content).metadata(metadata).build();
  }

  private static boolean isLoadedTag(Content content) {
    return content instanceof TextContent text && DocumentReferenceXmlTag.isLoaded(text);
  }

  /**
   * The ids of the documents loaded in the conversation before this turn, as the model reads it
   * through the context window.
   */
  private static Set<String> loadedDocumentIds(
      AgentConfiguration configuration, PreviousConversation previousConversation) {
    final var messages = new ArrayList<Message>();
    previousConversation.systemMessage().ifPresent(messages::add);
    for (var turn : previousConversation.turns()) {
      messages.addAll(turn.inputMessages());
      if (turn.assistantMessage() != null) {
        messages.add(turn.assistantMessage());
      }
    }
    return AgentConversation.loadedDocumentIds(
        MessageWindowFilter.apply(messages, configuration.contextWindowSize()));
  }

  /**
   * The note that tells the model a document was not loaded for it and why. A reason that a retry
   * can overcome points at {@code c8__get_document}.
   */
  private static String notLoadedNote(String documentId, Rejection rejection) {
    return "Document %s was not loaded automatically: it %s. %s"
        .formatted(
            documentId,
            rejection.problem(),
            rejection.retryable()
                ? "c8__get_document can try to load it again."
                : "Pass its id to a tool that processes it.");
  }

  private void ingestToolCallResultDocuments(
      List<ToolCallResult> results,
      MessageId toolCallResultMessageId,
      List<IngestedDocument> ingestedDocuments) {
    for (var group : documentExtractor.extractDocuments(results)) {
      final var source =
          DocumentSource.toolCall(
              group.toolCallName(), group.toolCallId(), toolCallResultMessageId.toString());
      group.documents().forEach(doc -> ingestedDocuments.add(new IngestedDocument(doc, source)));
    }
  }

  private @Nullable UserMessage createLegacyDocumentMessageForToolResults(
      List<ToolCallResult> results) {
    final var toolCallDocuments = documentExtractor.extractDocuments(results);
    if (toolCallDocuments.isEmpty()) {
      return null;
    }

    final var content = new ArrayList<Content>();
    content.add(textContent(TOOL_CALL_DOCUMENTS_PREAMBLE));
    content.addAll(createLegacyDocumentPairs(toolCallDocuments));

    final var metadata = new HashMap<>(defaultMessageMetadata());
    metadata.put(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true);

    return UserMessage.builder().content(content).metadata(metadata).build();
  }

  private Message createEventMessage(
      ToolCallResult eventResult,
      boolean interruptToolCallsOnEventResults,
      boolean documentFlow,
      List<IngestedDocument> ingestedDocuments) {
    Object eventContent = eventResult.content();

    final var messageId = MessageUtil.generateId();
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
    final var eventDocuments = documentExtractor.extractDocuments(List.of(eventResult));
    if (!eventDocuments.isEmpty()) {
      userMessageContent.add(
          textContent(documentFlow ? EVENT_DOCUMENT_TAGS_PREAMBLE : EVENT_DOCUMENTS_PREAMBLE));
      if (documentFlow) {
        final var source = DocumentSource.event(messageId.toString());
        for (var group : eventDocuments) {
          for (var doc : group.documents()) {
            ingestedDocuments.add(new IngestedDocument(doc, source));
            userMessageContent.add(DocumentReferenceXmlTag.from(doc, source).toContent());
          }
        }
      } else {
        userMessageContent.addAll(createLegacyDocumentPairs(eventDocuments));
      }
    }

    return UserMessage.builder()
        .id(messageId)
        .content(userMessageContent)
        .metadata(defaultMessageMetadata())
        .build();
  }

  /**
   * The documents of an agent instance without the documents feature: per document the tag in the
   * attribute format of that instance (see {@link LegacyDocumentReferenceXmlTag}) followed by the
   * document content.
   */
  private List<Content> createLegacyDocumentPairs(
      List<ToolCallResultDocumentExtractor.ToolCallDocuments> documentGroups) {
    final var content = new ArrayList<Content>();
    for (var group : documentGroups) {
      for (var doc : group.documents()) {
        content.add(
            textContent(
                LegacyDocumentReferenceXmlTag.from(doc, group.toolCallId(), group.toolCallName())
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

  private Map<String, Object> defaultMessageMetadata() {
    return Map.of("timestamp", ZonedDateTime.now());
  }
}
