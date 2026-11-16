/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContent.textContent;

import io.camunda.connector.agenticai.aiagent.api.tool.ToolCallEffect;
import io.camunda.connector.agenticai.aiagent.document.DocumentReferenceXmlTag;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocument;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentHandle;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentIdCollisionException;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentOrigin;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandling;
import io.camunda.connector.agenticai.aiagent.model.document.ResponseDocumentSelection;
import io.camunda.connector.agenticai.aiagent.model.document.UnsupportedDocumentReferenceException;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.AgentDocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the input of the turn that follows a batch of tool calls from its results, in call order.
 * It applies what the results ask of the agent after all calls ran: it returns the documents of the
 * results in the {@link TurnInput}, which {@link TurnInput#applyTo} registers, it applies the
 * effects of the results, and it returns the response documents that the results select in the
 * {@link TurnInput}, in call order, which {@link TurnInput#applyTo} adds to the agent context. It
 * is the one place that checks that an effect names a known document.
 *
 * <p>An agent instance that tracks its documents shows a document as its handle inside the tool
 * result and reports it in the {@link TurnInput} to be registered in its documents. For every
 * document whose content a result asks for, the builder appends the sentence "The content of {ref}
 * follows in the next message." to that result. The content follows in a message of its own. A
 * request always adds the content, also when an earlier turn added it. When several results of the
 * batch ask for the same document, the message holds the document once. It lists the documents in
 * the order of their first request.
 *
 * <p>An agent instance that does not track its documents keeps the document in a message of its
 * own, next to its tag. Such an instance gives no document an id, so a result of it must ask
 * nothing of the agent.
 */
public class ToolCallResultsInputBuilder {

  static final String TOOL_CALL_DOCUMENTS_PREAMBLE =
      "Documents extracted from tool calls (<doc /> tag + content pair):";

  static final String DOCUMENT_CONTENT_PREAMBLE =
      "Document content from tool calls (added by the system):";

  /** The sentence for a document whose content follows in the next message. */
  static String contentFollows(String documentRef) {
    return "The content of %s follows in the next message.".formatted(documentRef);
  }

  /**
   * Builds the input of the turn that follows the batch.
   *
   * @throws IllegalStateException if a call of the batch has no result
   * @throws UnsupportedDocumentReferenceException if a result of an agent instance that tracks its
   *     documents holds a document that is not a Camunda document
   * @throws IllegalArgumentException if an effect of a result names a document that is neither
   *     known to {@code agentContext} as of the start of the batch nor returned by the result, or
   *     if a result of an agent instance that does not track its documents has an effect
   * @throws AgentDocumentIdCollisionException if a document of a result gets the id of a different
   *     document that the agent knows, or of another document of the batch
   */
  public TurnInput build(AgentContext agentContext, ToolCallBatch batch) {
    final var entries = batch.entriesInCallOrder();
    return switch (agentContext.documentHandling()) {
      case DocumentHandling.Tracked tracked -> trackedInput(tracked.documents(), entries);
      case DocumentHandling.Untracked ignored -> buildWithDocumentMessage(entries);
    };
  }

  /** The input for an agent instance that tracks its documents. */
  private TurnInput trackedInput(AgentDocuments documents, List<ToolCallBatch.Entry> entries) {
    final var applied = applyEffects(documents, entries);
    final var input = buildWithDocumentBlocks(entries, applied);
    return new TurnInput(input.messages(), input.documents(), applied.responseDocuments());
  }

  /**
   * What the effects of the entries add to the conversation.
   *
   * @param statements for every entry, in the order of the entries, the sentences that tell what
   *     happens to the content of the documents it asked for
   * @param delivered the documents whose content is added, each one once, in the order of the first
   *     request
   * @param responseDocuments the response documents to select, in call order
   */
  private record AppliedEffects(
      List<List<Content>> statements,
      List<AgentDocumentContent> delivered,
      List<ResponseDocumentSelection> responseDocuments) {}

  /**
   * Applies the effects in the order of the entries. An entry sees the documents of the agent and
   * its own documents only, never the ones of another entry. An effect that names another document
   * fails. Every document that an entry asks for gets a sentence in the result of the entry. The
   * content of a document that several entries ask for is added once.
   */
  private AppliedEffects applyEffects(AgentDocuments documents, List<ToolCallBatch.Entry> entries) {
    final var statements = new ArrayList<List<Content>>();
    final var requestedByTheBatch = new LinkedHashSet<AgentDocumentId>();
    final var responseDocuments = new ArrayList<ResponseDocumentSelection>();

    for (final var entry : entries) {
      if (entry.effects().isEmpty()) {
        statements.add(List.of());
        continue;
      }

      final var known = documents.register(newDocumentsOf(entry));
      requireKnown(entry, known);
      final var requestedByTheEntry = new LinkedHashSet<AgentDocumentId>();
      for (final var effect : entry.effects()) {
        switch (effect) {
          case ToolCallEffect.AddDocumentContent add -> requestedByTheEntry.add(add.id());
          case ToolCallEffect.SelectResponseDocument select ->
              responseDocuments.add(select.selection());
        }
      }
      final var entryStatements = new ArrayList<Content>();
      for (final var id : requestedByTheEntry) {
        entryStatements.add(textContent(contentFollows(documentRef(known, id))));
      }
      statements.add(List.copyOf(entryStatements));
      requestedByTheBatch.addAll(requestedByTheEntry);
    }
    final var delivered =
        requestedByTheBatch.stream()
            .map(id -> AgentDocumentContent.of(id).withDelivery(DocumentDelivery.CONTENT))
            .toList();
    return new AppliedEffects(List.copyOf(statements), delivered, List.copyOf(responseDocuments));
  }

  /** The id of the document that an effect names. */
  private static AgentDocumentId documentOf(ToolCallEffect effect) {
    return switch (effect) {
      case ToolCallEffect.AddDocumentContent add -> add.id();
      case ToolCallEffect.SelectResponseDocument select -> select.selection().agentDocumentId();
    };
  }

  private static void requireKnown(ToolCallBatch.Entry entry, AgentDocuments known) {
    entry.effects().stream()
        .map(ToolCallResultsInputBuilder::documentOf)
        .filter(id -> known.find(id).isEmpty())
        .findFirst()
        .ifPresent(id -> {
          throw new IllegalArgumentException(
              "The runtime tool '%s' refers to the document '%s', which the agent does not know and the result does not return"
                  .formatted(entry.result().name(), id));
        });
  }

  /** The file name and the id of a document, or the id alone when it has no file name. */
  private static String documentRef(AgentDocuments known, AgentDocumentId id) {
    final var fileName = AgentDocumentHandle.of(known, id).fileName();
    return fileName == null ? id.value() : "%s (%s)".formatted(fileName, id.value());
  }

  private static List<AgentDocument> newDocumentsOf(ToolCallBatch.Entry entry) {
    final var origin = AgentDocumentOrigin.toolCall(entry.result().name(), entry.result().id());
    return entry.documents().stream().map(document -> AgentDocument.of(document, origin)).toList();
  }

  private static List<Content> concat(List<Content> first, List<Content> second) {
    final var all = new ArrayList<>(first);
    all.addAll(second);
    return List.copyOf(all);
  }

  private static TurnInput buildWithDocumentMessage(List<ToolCallBatch.Entry> entries) {
    entries.stream()
        .filter(entry -> !entry.effects().isEmpty())
        .findFirst()
        .ifPresent(entry -> {
          throw new IllegalArgumentException(
              "The runtime tool '%s' refers to a document by its id, which an agent instance that does not track its documents does not support"
                  .formatted(entry.result().name()));
        });
    final var messages = new ArrayList<Message>();
    messages.add(toolCallResultMessage(entries.stream().map(ToolCallBatch.Entry::result).toList()));

    final var documentGroups =
        entries.stream()
            .filter(entry -> !entry.documents().isEmpty())
            .map(entry ->
                new ToolCallResultDocumentExtractor.ToolCallDocuments(
                    entry.result().id(), entry.result().name(), entry.documents()))
            .toList();
    if (!documentGroups.isEmpty()) {
      messages.add(
          toolCallDocumentsMessage(TOOL_CALL_DOCUMENTS_PREAMBLE, documentPairs(documentGroups)));
    }
    return new TurnInput(messages);
  }

  private static ToolCallResultMessage toolCallResultMessage(List<ToolCallResultContent> results) {
    return ToolCallResultMessage.builder()
        .results(results)
        .metadata(MessageMetadata.defaults())
        .build();
  }

  /**
   * The user message that follows the tool results with their documents. {@link
   * UserMessage#METADATA_TOOL_CALL_DOCUMENTS} marks it for the message window.
   */
  private static UserMessage toolCallDocumentsMessage(String preamble, List<Content> documents) {
    final var content = new ArrayList<Content>();
    content.add(textContent(preamble));
    content.addAll(documents);
    final var metadata = new HashMap<>(MessageMetadata.defaults());
    metadata.put(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true);
    return UserMessage.builder().content(content).metadata(metadata).build();
  }

  /** A document tag followed by the document, for every document of every group. */
  static List<Content> documentPairs(
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
}
