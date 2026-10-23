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
import io.camunda.connector.agenticai.aiagent.conversation.PreviousConversation;
import io.camunda.connector.agenticai.aiagent.document.DocumentReferences;
import io.camunda.connector.agenticai.aiagent.document.IngestedDocument;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.request.EventHandlingConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.api.error.ConnectorException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
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

  static final String EVENT_DOCUMENT_TAGS_PREAMBLE = "Documents attached to the event:";

  // the preamble of the tag and content pairs of an agent instance without the documents feature
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
    return compose(configuration, agentContext, previousConversation, agentInput, List.of());
  }

  @Override
  public CompositionResult composeWithRuntimeToolResults(
      AgentConfiguration configuration,
      AgentContext agentContext,
      PreviousConversation previousConversation,
      AgentInput agentInput,
      List<RuntimeToolExecution> executions) {
    requireExecutionsOfTheRuntimeToolCalls(previousConversation, executions);
    return compose(
        configuration, agentContext, previousConversation, agentInput, List.copyOf(executions));
  }

  /**
   * @throws IllegalStateException if the executions are not exactly the executions of the runtime
   *     tool calls of the last turn of the previous conversation
   */
  private void requireExecutionsOfTheRuntimeToolCalls(
      PreviousConversation previousConversation, List<RuntimeToolExecution> executions) {
    final var expectedIds =
        previousConversation.turns().isEmpty()
            ? List.<String>of()
            : previousConversation.turns().getLast().runtimeToolCalls().stream()
                .map(ToolCall::id)
                .toList();
    final var givenIds = executions.stream().map(execution -> execution.call().id()).toList();
    if (givenIds.size() != expectedIds.size()
        || !new HashSet<>(givenIds).equals(new HashSet<>(expectedIds))) {
      throw new IllegalStateException(
          "The executions must be exactly the executions of the runtime tool calls of the previous turn. Expected the tool call ids %s but got %s."
              .formatted(expectedIds, givenIds));
    }
  }

  /**
   * @param executions the executions of the runtime tool calls of the previous turn, empty while
   *     none is given
   */
  private CompositionResult compose(
      AgentConfiguration configuration,
      AgentContext agentContext,
      PreviousConversation previousConversation,
      AgentInput agentInput,
      List<RuntimeToolExecution> executions) {
    // tool call results arriving without a previous conversation is most likely a modeling error
    if (agentContext.conversation() == null && !agentInput.toolCallResults().isEmpty()) {
      throw new ConnectorException(
          ERROR_CODE_TOOL_CALL_RESULTS_ON_EMPTY_CONTEXT,
          "Agent received tool call results, but the agent context was empty (no previous conversation). Is the context configured correctly?");
    }

    boolean interruptToolCallsOnEventResults = interruptToolCallsOnEventResults(configuration);
    final boolean documentFlow = agentContext.supports(AgentFeatures.DOCUMENTS);

    // the documents that enter the conversation with this turn: those of the prompt or of the
    // tool call results first, those of the events after them
    final var ingestedDocuments = new ArrayList<IngestedDocument>();
    final var eventDocuments = new ArrayList<IngestedDocument>();

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

      final var previousTurn = previousConversation.turns().getLast();

      final var processResults =
          resolveProcessToolCallResults(
              agentContext,
              previousTurn.processToolCalls(),
              agentInput.toolCallResults(),
              interruptMissingToolCalls);

      // either we have all results or we interrupted the missing tool calls
      // if incomplete, we wait on further tool call results to be added
      if (!processResults.complete()) {
        return new CompositionResult.Deferred(processResults.arrivedResults());
      }
      if (previousTurn.hasRuntimeToolCalls() && executions.isEmpty()) {
        return new CompositionResult.RuntimeToolResultsRequired(previousTurn.runtimeToolCalls());
      }

      final var toolCallResultsInput =
          ToolCallResultsInputBuilder.build(
              orderedEntries(previousTurn.toolCalls(), processResults.resultsById(), executions),
              documentFlow);
      messages.addAll(toolCallResultsInput.messages());
      ingestedDocuments.addAll(toolCallResultsInput.documents());
      messages.addAll(eventMessages);
    } else {
      messages.add(
          createUserPromptMessage(agentInput.userPrompt(), documentFlow, ingestedDocuments));
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
      List<IngestedDocument> ingestedDocuments) {

    final var content = new ArrayList<Content>();

    // add user prompt text
    final var userPromptText = userPrompt.prompt();
    if (StringUtils.isNotBlank(userPromptText)) {
      content.add(textContent(userPromptText));
    }

    final var source = DocumentSource.userMessage();
    if (documentFlow) {
      // add documents as references with the delivery content: the content is added, from the
      // registry, when the model is called
      for (var doc : userPrompt.documents()) {
        content.add(DocumentReferences.from(doc, source).withDelivery(DocumentDelivery.CONTENT));
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
        .content(content)
        .metadata(ToolCallResultsInputBuilder.defaultMessageMetadata())
        .build();
  }

  /**
   * @param resultsById the results of the process tool calls by tool call id: the correlated and
   *     gateway-transformed results and, when interrupting, the cancelled ones; empty when {@code
   *     complete} is {@code false}
   * @param arrivedResults the process results that arrived (correlated and gateway-transformed), in
   *     tool-call order
   * @param complete whether every process tool call has a result or was cancelled via interrupt
   */
  private record ProcessToolCallResults(
      Map<String, ToolCallResult> resultsById,
      List<ToolCallResult> arrivedResults,
      boolean complete) {}

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
    processToolCalls.forEach(
        toolCall -> {
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
      return new ProcessToolCallResults(Map.of(), arrivedToolCallResults, false);
    }

    final var resultsById = new HashMap<String, ToolCallResult>();
    arrivedToolCallResults.forEach(result -> resultsById.put(result.id(), result));
    missingToolCalls.forEach(
        toolCall -> {
          LOGGER.debug("Missing tool call result for ID: {}. Marking as canceled.", toolCall.id());
          resultsById.put(
              toolCall.id(),
              ToolCallResult.forCancelledToolCall(
                  toolCall.id(), toolCall.name(), OffsetDateTime.now()));
        });
    return new ProcessToolCallResults(resultsById, arrivedToolCallResults, true);
  }

  /**
   * The results of all tool calls of the turn in call order: the process results, with the
   * documents extracted from the result as the process returned it, and the results of the runtime
   * tools, with the documents of their content.
   */
  private List<ToolCallResultsInputBuilder.Entry> orderedEntries(
      List<ToolCall> toolCalls,
      Map<String, ToolCallResult> processResultsById,
      List<RuntimeToolExecution> executions) {
    final var executionsById =
        executions.stream()
            .collect(Collectors.toMap(execution -> execution.call().id(), Function.identity()));
    return toolCalls.stream()
        .map(
            toolCall ->
                switch (toolCall.kind()) {
                  case PROCESS ->
                      processEntry(
                          Objects.requireNonNull(
                              processResultsById.get(toolCall.id()),
                              "No result of the process tool call '%s' was resolved"
                                  .formatted(toolCall.id())));
                  case RUNTIME -> runtimeEntry(toolCall, executionsById.get(toolCall.id()));
                })
        .toList();
  }

  private ToolCallResultsInputBuilder.Entry processEntry(ToolCallResult result) {
    final var documents =
        documentExtractor.extractDocuments(List.of(result)).stream()
            .flatMap(group -> group.documents().stream())
            .toList();
    return new ToolCallResultsInputBuilder.Entry(ToolCallResultContent.from(result), documents);
  }

  private ToolCallResultsInputBuilder.Entry runtimeEntry(
      ToolCall toolCall, @Nullable RuntimeToolExecution execution) {
    if (execution == null) {
      throw new IllegalStateException(
          "No execution of the runtime tool call '%s' was given".formatted(toolCall.id()));
    }
    return ToolCallResultsInputBuilder.Entry.of(execution);
  }

  private Message createEventMessage(
      ToolCallResult eventResult,
      boolean interruptToolCallsOnEventResults,
      boolean documentFlow,
      List<IngestedDocument> ingestedDocuments) {
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

    // events arrive as ToolCallResult with null id/name, so their documents have no tool call
    final var eventDocuments = documentExtractor.extractDocuments(List.of(eventResult));
    if (!eventDocuments.isEmpty()) {
      userMessageContent.add(
          textContent(documentFlow ? EVENT_DOCUMENT_TAGS_PREAMBLE : EVENT_DOCUMENTS_PREAMBLE));
      if (documentFlow) {
        final var source = DocumentSource.event();
        for (var group : eventDocuments) {
          for (var doc : group.documents()) {
            ingestedDocuments.add(new IngestedDocument(doc, source));
            userMessageContent.add(DocumentReferences.from(doc, source));
          }
        }
      } else {
        userMessageContent.addAll(ToolCallResultsInputBuilder.documentPairs(eventDocuments));
      }
    }

    return UserMessage.builder()
        .content(userMessageContent)
        .metadata(ToolCallResultsInputBuilder.defaultMessageMetadata())
        .build();
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
