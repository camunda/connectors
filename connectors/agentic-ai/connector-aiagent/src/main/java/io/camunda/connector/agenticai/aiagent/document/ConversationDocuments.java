/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.camunda.connector.agenticai.aiagent.api.tool.AgentDocument;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolDocuments;
import io.camunda.connector.agenticai.aiagent.memory.runtime.MessageWindowFilter;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentReferenceContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The documents of the messages of a conversation, in the order they appear in the messages: the
 * document references of user messages (user prompts and events) and the documents of tool results,
 * as a reference or nested in an object. Assistant messages are not read, the model writes them.
 * The first occurrence of an id wins.
 */
public final class ConversationDocuments implements RuntimeToolDocuments {

  private static final ConversationDocuments EMPTY = new ConversationDocuments(Map.of());

  private static final String COLLECTED_DOCUMENTS = "collectedDocuments";

  private static final ObjectMapper COLLECTING_MAPPER =
      ConnectorsObjectMapperSupplier.getCopy()
          .registerModule(new SimpleModule().addSerializer(new DocumentCollector()));

  private final Map<DocumentId, AgentDocument> documentsById;

  private ConversationDocuments(Map<DocumentId, AgentDocument> documentsById) {
    this.documentsById = documentsById;
  }

  public static ConversationDocuments empty() {
    return EMPTY;
  }

  /**
   * The documents the model saw when it was called last: the documents of the context window of the
   * messages before the last assistant message. Empty for an agent instance without the documents
   * feature.
   *
   * @param messages the messages of the conversation, ending with the assistant message of the last
   *     model call if there is one
   * @param contextWindowSize the size of the context window the model was called with
   */
  public static ConversationDocuments seenByModel(
      AgentContext agentContext, List<Message> messages, int contextWindowSize) {
    if (!agentContext.supports(AgentFeatures.DOCUMENTS)) {
      return EMPTY;
    }
    final var beforeAnswer =
        !messages.isEmpty() && messages.getLast() instanceof AssistantMessage
            ? messages.subList(0, messages.size() - 1)
            : messages;
    return of(MessageWindowFilter.apply(beforeAnswer, contextWindowSize));
  }

  /** The documents of the given messages. */
  public static ConversationDocuments of(List<Message> messages) {
    final var byId = new LinkedHashMap<DocumentId, AgentDocument>();
    for (final var message : messages) {
      switch (message) {
        case UserMessage userMessage -> {
          for (final var content : userMessage.content()) {
            if (content instanceof DocumentReferenceContent reference) {
              add(byId, reference, DocumentSource.userMessage());
            }
          }
        }
        case ToolCallResultMessage toolCallResultMessage -> {
          for (final var result : toolCallResultMessage.results()) {
            final var source = DocumentSource.toolCall(result.name(), result.id());
            result.content().forEach(content -> addOfResult(byId, content, source));
          }
        }
        default -> {
          // only user messages and tool results bring documents into the conversation
        }
      }
    }
    return byId.isEmpty() ? EMPTY : new ConversationDocuments(Collections.unmodifiableMap(byId));
  }

  @Override
  public Optional<AgentDocument> find(DocumentId id) {
    return Optional.ofNullable(documentsById.get(id));
  }

  @Override
  public List<AgentDocument> all() {
    return List.copyOf(documentsById.values());
  }

  @Override
  public DocumentReferenceContent reference(DocumentId id) {
    final var document =
        find(id)
            .orElseThrow(
                () -> new IllegalArgumentException("The agent does not know the document " + id));
    return DocumentReferences.from(document.document(), document.source());
  }

  private static void addOfResult(
      Map<DocumentId, AgentDocument> byId, Content content, DocumentSource source) {
    switch (content) {
      case DocumentReferenceContent reference -> add(byId, reference, source);
      case ObjectContent objectContent ->
          nestedDocuments(objectContent.content())
              .forEach(
                  document ->
                      byId.putIfAbsent(
                          DocumentHandle.idFor(document),
                          new ConversationDocument(
                              DocumentHandle.idFor(document), source, document)));
      default -> {
        // other blocks hold no document
      }
    }
  }

  private static void add(
      Map<DocumentId, AgentDocument> byId,
      DocumentReferenceContent reference,
      DocumentSource fallbackSource) {
    final var source = reference.source() != null ? reference.source() : fallbackSource;
    byId.putIfAbsent(
        reference.id(), new ConversationDocument(reference.id(), source, reference.document()));
  }

  /** The documents at any depth of an object, typed or plain JSON values. */
  private static List<Document> nestedDocuments(Object value) {
    final var documents = new ArrayList<Document>();
    try {
      COLLECTING_MAPPER
          .writer()
          .withAttribute(COLLECTED_DOCUMENTS, documents)
          .writeValue(OutputStream.nullOutputStream(), value);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return documents;
  }

  private record ConversationDocument(DocumentId id, DocumentSource source, Document document)
      implements AgentDocument {}

  private static final class DocumentCollector extends JsonSerializer<Document> {

    @Override
    public Class<Document> handledType() {
      return Document.class;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void serialize(Document document, JsonGenerator gen, SerializerProvider provider)
        throws IOException {
      final var collected = (List<Document>) provider.getAttribute(COLLECTED_DOCUMENTS);
      if (collected != null) {
        collected.add(document);
      }
      gen.writeNull();
    }
  }
}
