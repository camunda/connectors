/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContent.textContent;

import io.camunda.connector.agenticai.aiagent.api.tool.ToolCallEffect;
import io.camunda.connector.agenticai.aiagent.document.DocumentContentDelivery;
import io.camunda.connector.agenticai.aiagent.document.DocumentReferenceXmlTag;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocument;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentHandle;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentIdCollisionException;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentOrigin;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandling;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.AgentDocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * Builds the input of the turn that follows a batch of tool calls from its results, in call order.
 * It applies what the results ask of the agent after all calls ran: it returns the documents of the
 * results in the {@link TurnInput}, which {@link TurnInput#applyTo} registers, and it applies the
 * effects of the results. It is the one place that checks that an effect names a known document.
 *
 * <p>An agent instance that tracks its documents shows a document as its handle inside the tool
 * result and reports it in the {@link TurnInput} to be registered in its documents. For every
 * document whose content a result asks for, the builder appends a sentence to that result that
 * tells the model what happens: the content follows in the next message, or the content is in the
 * conversation already. The documents whose content is added follow in a message of their own. An
 * agent instance that does not track its documents keeps the document in a message of its own, next
 * to its tag. Such an instance gives no document an id, so a result of it must ask nothing of the
 * agent.
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

  /** The sentence for a document whose content the model reads already. */
  static String contentAlreadyInConversation(String documentRef) {
    return "The content of %s is already in the conversation.".formatted(documentRef);
  }

  /**
   * Builds the input of the turn that follows the batch.
   *
   * @param documentIdsWithContent the ids of the documents whose content the model reads as of the
   *     start of the batch
   * @throws IllegalStateException if a call of the batch has no result
   * @throws IllegalArgumentException if a result asks for the content of a document that is neither
   *     known to {@code agentContext} as of the start of the batch nor returned by the result, or
   *     if a result of an agent instance that does not track its documents asks anything of the
   *     agent
   * @throws AgentDocumentIdCollisionException if a document of a result gets the id of a different
   *     document that the agent knows, or of another document of the batch
   */
  public TurnInput build(
      AgentContext agentContext, ToolCallBatch batch, Set<AgentDocumentId> documentIdsWithContent) {
    final var entries = batch.entriesInCallOrder();
    return switch (agentContext.documentHandling()) {
      case DocumentHandling.Tracked tracked ->
          trackedInput(tracked.documents(), entries, documentIdsWithContent);
      case DocumentHandling.Untracked ignored -> buildWithDocumentMessage(entries);
    };
  }

  /** The input for an agent instance that tracks its documents. */
  private TurnInput trackedInput(
      AgentDocuments documents,
      List<ToolCallBatch.Entry> entries,
      Set<AgentDocumentId> documentIdsWithContent) {
    return buildWithDocumentBlocks(
        entries, applyEffects(documents, entries, documentIdsWithContent));
  }

  /**
   * What the effects of the entries add to the conversation.
   *
   * @param statements for every entry, in the order of the entries, the sentences that tell what
   *     happens to the content of the documents it asked for
   * @param delivered the documents whose content is added, in the order of the entries
   */
  private record AppliedEffects(
      List<List<Content>> statements, List<AgentDocumentContent> delivered) {}

  /**
   * Applies the effects in the order of the entries. An entry sees the documents of the agent and
   * its own documents only, never the ones of another entry. An effect that names another document
   * fails.
   */
  private AppliedEffects applyEffects(
      AgentDocuments documents,
      List<ToolCallBatch.Entry> entries,
      Set<AgentDocumentId> documentIdsWithContent) {
    var delivery = DocumentContentDelivery.of(documentIdsWithContent);
    final var statements = new ArrayList<List<Content>>();
    final var delivered = new ArrayList<AgentDocumentContent>();

    for (final var entry : entries) {
      final var requestedContent = new ArrayList<AgentDocumentId>();
      for (final var effect : entry.effects()) {
        switch (effect) {
          case ToolCallEffect.AddDocumentContent add -> requestedContent.add(add.id());
        }
      }
      if (requestedContent.isEmpty()) {
        statements.add(List.of());
        continue;
      }

      final var known = documents.register(newDocumentsOf(entry));
      requireKnown(entry, requestedContent, known);
      final var entryStatements = new ArrayList<Content>();
      final var decision = delivery.deliver(requestedContent);
      delivery = decision.next();
      for (final var outcome : decision.outcomes()) {
        switch (outcome) {
          case DocumentContentDelivery.Outcome.Delivered added -> {
            delivered.add(added.withContent());
            entryStatements.add(
                textContent(contentFollows(documentRef(known, added.withContent().id()))));
          }
          case DocumentContentDelivery.Outcome.AlreadyInConversation already ->
              entryStatements.add(
                  textContent(contentAlreadyInConversation(documentRef(known, already.id()))));
        }
      }
      statements.add(List.copyOf(entryStatements));
    }
    return new AppliedEffects(List.copyOf(statements), List.copyOf(delivered));
  }

  private static void requireKnown(
      ToolCallBatch.Entry entry, List<AgentDocumentId> requested, AgentDocuments known) {
    requested.stream()
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

  /**
   * A document that is a whole result is persisted as a document block, like the documents that are
   * not a whole result. The documents of every result are registered with the tool call as their
   * origin. A result that asks for the content of documents ends with a sentence per document. The
   * content of the documents that are loaded follows in a message of its own.
   */
  private static TurnInput buildWithDocumentBlocks(
      List<ToolCallBatch.Entry> entries, AppliedEffects applied) {
    final var results = new ArrayList<ToolCallResultContent>();
    for (int i = 0; i < entries.size(); i++) {
      final var result = entries.get(i).result().withDocumentsAsReferences();
      final var statements = applied.statements().get(i);
      results.add(
          statements.isEmpty() ? result : result.withContent(concat(result.content(), statements)));
    }
    final var documents = new ArrayList<AgentDocument>();
    entries.forEach(entry -> documents.addAll(newDocumentsOf(entry)));

    final var messages = new ArrayList<Message>();
    messages.add(
        ToolCallResultMessage.builder()
            .results(results)
            .metadata(MessageMetadata.defaults())
            .build());
    if (!applied.delivered().isEmpty()) {
      final var content = new ArrayList<Content>();
      content.add(textContent(DOCUMENT_CONTENT_PREAMBLE));
      content.addAll(applied.delivered());

      final var metadata = new HashMap<>(MessageMetadata.defaults());
      metadata.put(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true);
      messages.add(UserMessage.builder().content(content).metadata(metadata).build());
    }
    return new TurnInput(messages, documents);
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
    messages.add(
        ToolCallResultMessage.builder()
            .results(entries.stream().map(ToolCallBatch.Entry::result).toList())
            .metadata(MessageMetadata.defaults())
            .build());

    final var documentGroups =
        entries.stream()
            .filter(entry -> !entry.documents().isEmpty())
            .map(entry ->
                new ToolCallResultDocumentExtractor.ToolCallDocuments(
                    entry.result().id(), entry.result().name(), entry.documents()))
            .toList();
    if (!documentGroups.isEmpty()) {
      final var content = new ArrayList<Content>();
      content.add(textContent(TOOL_CALL_DOCUMENTS_PREAMBLE));
      content.addAll(documentPairs(documentGroups));

      final var metadata = new HashMap<>(MessageMetadata.defaults());
      metadata.put(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true);
      messages.add(UserMessage.builder().content(content).metadata(metadata).build());
    }
    return new TurnInput(messages);
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
