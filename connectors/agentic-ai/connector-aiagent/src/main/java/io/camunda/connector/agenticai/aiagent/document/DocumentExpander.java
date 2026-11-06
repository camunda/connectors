/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import io.camunda.connector.agenticai.aiagent.api.conversation.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandling;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.AgentDocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.api.document.DocumentReference.ExternalDocumentReference;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Expands the {@link AgentDocumentContent document blocks} of the conversation into the form the
 * chat model providers see, so that no provider has document specific code.
 *
 * <p>The persisted form of a document is its block only; the document is restored from the agent
 * documents. In a user message the model gets the handle text of every block and the document
 * content of the blocks with {@link DocumentDelivery#CONTENT}. A document the agent does not know,
 * or that cannot be restored, leaves the handle text alone. A handle a user typed into a prompt is
 * text and stays text.
 *
 * <p>In a tool result a block becomes the handle text of the document, which for the model is the
 * handle only: the delivery of such a block is not read. A document nested in an object is replaced
 * by its handle object in the object, in a tool result as well as in a user message.
 *
 * <p>The result depends only on the messages and the agent documents, never on time or call order,
 * so it is stable across requests (prompt caching). Nothing that is stored is changed.
 */
public final class DocumentExpander {

  private final DocumentRestorer restorer;
  private final ObjectHandleRenderer objectHandleRenderer;

  public DocumentExpander(DocumentRestorer restorer, ObjectHandleRenderer objectHandleRenderer) {
    this.restorer = restorer;
    this.objectHandleRenderer = objectHandleRenderer;
  }

  /**
   * The snapshot the model is called with: for an agent instance that tracks its documents the
   * messages are expanded, for one that does not they are left as they are.
   */
  public ConversationSnapshot expand(ConversationSnapshot snapshot, DocumentHandling handling) {
    return switch (handling) {
      case DocumentHandling.Untracked ignored -> snapshot;
      case DocumentHandling.Tracked tracked ->
          new ConversationSnapshot(
              expandMessages(snapshot.messages(), tracked.documents()), snapshot.toolDefinitions());
    };
  }

  /**
   * The messages expanded for the model: in a user message the handle text of every document block,
   * followed by the document for the blocks with the delivery {@code content}; in a tool result the
   * handle text of every block. In both, a document nested in an object is replaced by its handle.
   */
  List<Message> expandMessages(List<Message> messages, AgentDocuments documents) {
    final var expanded = new ArrayList<Message>(messages.size());
    for (final var message : messages) {
      expanded.add(
          switch (message) {
            case UserMessage userMessage ->
                userMessage.withContent(expandUserContent(userMessage.content(), documents));
            case ToolCallResultMessage resultMessage ->
                resultMessage.withResults(expandResults(resultMessage.results(), documents));
            default -> message;
          });
    }
    return List.copyOf(expanded);
  }

  private List<Content> expandUserContent(List<Content> contents, AgentDocuments documents) {
    final var expanded = new ArrayList<Content>(contents.size());
    for (int i = 0; i < contents.size(); i++) {
      switch (contents.get(i)) {
        case AgentDocumentContent block -> {
          expanded.add(new TextContent(handleText(documents, block.id()), block.metadata()));
          final var next = i + 1 < contents.size() ? contents.get(i + 1) : null;
          if (block.delivery() == DocumentDelivery.CONTENT
              && !isFollowedByDocument(next, block.id())) {
            restorer
                .restore(documents, block.id())
                .map(DocumentContent::documentContent)
                .ifPresent(expanded::add);
          }
        }
        case ObjectContent objectContent -> expanded.add(renderObject(objectContent));
        case TextContent content -> expanded.add(content);
        case DocumentContent content -> expanded.add(content);
        case ReasoningContent content -> expanded.add(content);
        case ProviderContent content -> expanded.add(content);
      }
    }
    return List.copyOf(expanded);
  }

  private List<ToolCallResultContent> expandResults(
      List<ToolCallResultContent> results, AgentDocuments documents) {
    return results.stream()
        .map(result -> result.withContent(expandResultContent(result.content(), documents)))
        .toList();
  }

  private List<Content> expandResultContent(List<Content> contents, AgentDocuments documents) {
    final var expanded = new ArrayList<Content>(contents.size());
    for (final var content : contents) {
      expanded.add(
          switch (content) {
            case AgentDocumentContent block ->
                new TextContent(handleText(documents, block.id()), block.metadata());
            case ObjectContent objectContent -> renderObject(objectContent);
            case TextContent textContent -> textContent;
            case DocumentContent documentContent -> documentContent;
            case ReasoningContent reasoningContent -> reasoningContent;
            case ProviderContent providerContent -> providerContent;
          });
    }
    return List.copyOf(expanded);
  }

  private static String handleText(AgentDocuments documents, AgentDocumentId id) {
    return DocumentHandle.of(documents, id).text();
  }

  /**
   * The object with its documents replaced by their handles, or the same object when it has none.
   */
  private ObjectContent renderObject(ObjectContent objectContent) {
    final var rendered = objectHandleRenderer.render(objectContent.content());
    return rendered.documents().isEmpty()
        ? objectContent
        : new ObjectContent(rendered.tree(), objectContent.metadata());
  }

  private static boolean isFollowedByDocument(@Nullable Content next, AgentDocumentId id) {
    return next instanceof DocumentContent following
        && hasHandle(following.document())
        && id.equals(AgentDocumentId.of(following.document()));
  }

  /** Only Camunda and external documents get a handle, inline documents are left as they are. */
  private static boolean hasHandle(Document document) {
    return document.reference() instanceof CamundaDocumentReference
        || document.reference() instanceof ExternalDocumentReference;
  }
}
