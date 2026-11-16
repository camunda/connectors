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
import io.camunda.connector.agenticai.aiagent.model.document.UnsupportedDocumentReferenceException;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.AgentDocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
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
   * @throws IllegalArgumentException if a result asks for the content of a document that is neither
   *     known to {@code agentContext} as of the start of the batch nor returned by the result, or
   *     if a result of an agent instance that does not track its documents asks anything of the
   *     agent
   * @throws AgentDocumentIdCollisionException if a document of a result that asks for content gets
   *     the id of a different document that the agent knows
   */
  public TurnInput build(AgentContext agentContext, ToolCallBatch batch) {
    final var entries = batch.entriesInCallOrder();
    return switch (agentContext.documentHandling()) {
      case DocumentHandling.Tracked tracked -> trackedInput(tracked.documents(), entries);
      case DocumentHandling.Untracked ignored -> buildWithDocumentMessage(entries);
    };
  }

  /**
   * The input for an agent instance that tracks its documents. The effects are applied in the order
   * of the entries. An entry sees the documents of the agent and its own documents only, never the
   * ones of another entry. The content of a document that several entries ask for is added once, in
   * the order of the first request.
   */
  private TurnInput trackedInput(AgentDocuments documents, List<ToolCallBatch.Entry> entries) {
    final var results = new ArrayList<ToolCallResultContent>();
    final var delivered = new LinkedHashSet<AgentDocumentId>();
    for (final var entry : entries) {
      final var result = entry.result().withDocumentsAsReferences();
      final var requested = requestedContent(entry);
      if (requested.isEmpty()) {
        results.add(result);
        continue;
      }
      results.add(
          result.withContent(
              concat(result.content(), contentStatements(documents, entry, requested))));
      delivered.addAll(requested);
    }

    final var messages = new ArrayList<Message>();
    messages.add(toolCallResultMessage(results));
    if (!delivered.isEmpty()) {
      messages.add(
          toolCallDocumentsMessage(
              DOCUMENT_CONTENT_PREAMBLE,
              delivered.stream()
                  .<Content>map(
                      id -> AgentDocumentContent.of(id).withDelivery(DocumentDelivery.CONTENT))
                  .toList()));
    }
    final var newDocuments = new ArrayList<AgentDocument>();
    entries.forEach(entry -> newDocuments.addAll(newDocumentsOf(entry)));
    return new TurnInput(messages, newDocuments);
  }

  /** The documents whose content the entry asks for, in the order of its effects. */
  private static Set<AgentDocumentId> requestedContent(ToolCallBatch.Entry entry) {
    final var requested = new LinkedHashSet<AgentDocumentId>();
    for (final var effect : entry.effects()) {
      switch (effect) {
        case ToolCallEffect.AddDocumentContent add -> requested.add(add.id());
      }
    }
    return requested;
  }

  /**
   * The sentences that end the result of the entry, one per requested document. Every requested
   * document must be known to the agent or returned by the entry.
   */
  private static List<Content> contentStatements(
      AgentDocuments documents, ToolCallBatch.Entry entry, Collection<AgentDocumentId> requested) {
    final var known = documents.register(newDocumentsOf(entry));
    requireKnown(entry, requested, known);
    return requested.stream()
        .<Content>map(id -> textContent(contentFollows(documentRef(known, id))))
        .toList();
  }

  private static void requireKnown(
      ToolCallBatch.Entry entry, Collection<AgentDocumentId> requested, AgentDocuments known) {
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
