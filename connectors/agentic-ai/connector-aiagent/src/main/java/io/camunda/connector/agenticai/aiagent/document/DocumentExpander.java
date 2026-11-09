/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import io.camunda.connector.agenticai.aiagent.api.conversation.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentHandle;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentDelivery;
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
import io.camunda.connector.api.document.DocumentFactory;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Expands the {@link AgentDocumentContent document blocks} of the conversation into the form the
 * chat model providers see, so that no provider has document specific code.
 *
 * <p>The persisted form of a document is its block only; the document is restored from the agent
 * documents. In a user message the model gets the handle text of every block and the document
 * content of the blocks with {@link DocumentDelivery#CONTENT}. A document the agent does not know
 * leaves the handle text alone. A handle a user typed into a prompt is text and stays text.
 *
 * <p>The content of an external document is downloaded again in every job, and the URL can serve
 * other content than when the {@link DocumentContentGuard} checked it. In the message with the
 * documents whose content a tool asked for ({@link UserMessage#METADATA_TOOL_CALL_DOCUMENTS}),
 * which holds only content that passed the guard, the content of an external document is therefore
 * checked again before every model call; a document that fails the check gets the note of the guard
 * after its handle text instead of its content. The documents of a user prompt are not checked
 * here. A Camunda document cannot change, so it is not checked again.
 *
 * <p>In a tool result a block becomes the handle text of the document, which for the model is the
 * handle only: the delivery of such a block is not read. A document nested in an object is replaced
 * by its handle object in the object, in a tool result as well as in a user message.
 *
 * <p>The result depends only on the messages, the agent documents and the content of the external
 * documents, never on time or call order, so it is stable across requests (prompt caching) as long
 * as an external document serves the same content. Nothing that is stored is changed.
 */
public class DocumentExpander {

  private static final Logger LOGGER = LoggerFactory.getLogger(DocumentExpander.class);

  private final ObjectHandleRenderer objectHandleRenderer;
  private final DocumentContentGuard contentGuard;

  public DocumentExpander(
      ObjectHandleRenderer objectHandleRenderer, DocumentContentGuard contentGuard) {
    this.objectHandleRenderer = objectHandleRenderer;
    this.contentGuard = contentGuard;
  }

  /**
   * The snapshot the model is called with: for an agent instance that tracks its documents the
   * messages are expanded, for one that does not they are left as they are.
   *
   * @param documentFactory the document factory of the job, which restores the documents in the
   *     document store of the physical tenant of the job
   */
  public ConversationSnapshot expand(
      ConversationSnapshot snapshot, DocumentHandling handling, DocumentFactory documentFactory) {
    return switch (handling) {
      case DocumentHandling.Untracked ignored -> snapshot;
      case DocumentHandling.Tracked tracked ->
          snapshot.withMessages(
              expandMessages(snapshot.messages(), tracked.documents(), documentFactory));
    };
  }

  /**
   * The messages expanded for the model: in a user message the handle text of every document block,
   * followed by the document for the blocks with the delivery {@code content}; in a tool result the
   * handle text of every block. In both, a document nested in an object is replaced by its handle.
   */
  List<Message> expandMessages(
      List<Message> messages, AgentDocuments documents, DocumentFactory documentFactory) {
    final var restorer = new DocumentRestorer(documentFactory);
    final var expanded = new ArrayList<Message>(messages.size());
    for (final var message : messages) {
      expanded.add(
          switch (message) {
            case UserMessage userMessage ->
                userMessage.withContent(
                    expandUserContent(
                        userMessage.content(), documents, restorer, isChecked(userMessage)));
            case ToolCallResultMessage resultMessage ->
                resultMessage.withResults(expandResults(resultMessage.results(), documents));
            default -> message;
          });
    }
    return List.copyOf(expanded);
  }

  private List<Content> expandUserContent(
      List<Content> contents,
      AgentDocuments documents,
      DocumentRestorer restorer,
      boolean checkedContent) {
    final var expanded = new ArrayList<Content>(contents.size());
    for (int i = 0; i < contents.size(); i++) {
      switch (contents.get(i)) {
        case AgentDocumentContent block -> {
          expanded.add(new TextContent(handleText(documents, block.id()), block.metadata()));
          if (block.delivery() == DocumentDelivery.CONTENT) {
            restorer
                .restore(documents, block.id())
                .map(document ->
                    checkedContent
                        ? documentContentOrNote(document, block.id())
                        : DocumentContent.documentContent(document))
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

  private static boolean isChecked(UserMessage message) {
    return Boolean.TRUE.equals(message.metadata().get(UserMessage.METADATA_TOOL_CALL_DOCUMENTS));
  }

  /**
   * The document, or the note of the guard when the content of an external document fails the check
   * again.
   */
  private Content documentContentOrNote(Document document, AgentDocumentId id) {
    if (document.reference() instanceof ExternalDocumentReference
        && contentGuard.check(document) instanceof DocumentContentGuard.Verdict.Rejected rejected) {
      LOGGER.debug(
          "Not adding the content of the external document {}: {}",
          id.value(),
          rejected.rejection().modelText(),
          rejected.rejection().cause());
      return TextContent.textContent(rejected.rejection().modelText());
    }
    return DocumentContent.documentContent(document);
  }

  private String handleText(AgentDocuments documents, AgentDocumentId id) {
    return objectHandleRenderer.text(AgentDocumentHandle.of(documents, id));
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
}
