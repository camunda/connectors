/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.model.document.IngestedDocument;
import io.camunda.connector.agenticai.aiagent.model.message.DocumentReferenceXmlTag;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.api.document.DocumentReference.ExternalDocumentReference;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Adds the documents of a stored conversation that the {@link DocumentRegistry} does not know yet.
 *
 * <p>Conversations stored in the form of earlier versions carry documents the registry never saw:
 * the content of documents in user messages and the documents in the content of tool call results.
 * Their stored messages stay as they are; the registry is the only thing that is extended. The
 * source of a document is derived from where it is found:
 *
 * <ul>
 *   <li>a document in a tool call result: {@link DocumentSource.Type#TOOL_CALL} with the tool of
 *       that result and the id of the tool call result message
 *   <li>a document in a user message that holds the documents extracted from tool calls: {@link
 *       DocumentSource.Type#TOOL_CALL}, with the tool read from the tag in front of the document
 *       and the id of the preceding tool call result message
 *   <li>a document in a user message after the event documents preamble: {@link
 *       DocumentSource.Type#EVENT}
 *   <li>any other document in a user message: {@link DocumentSource.Type#USER_MESSAGE}
 * </ul>
 *
 * Only Camunda and external documents are added; inline documents are not. Documents the registry
 * already knows are left as they are, so a conversation without missing documents leaves the
 * registry unchanged.
 */
public final class DocumentRegistryBackfill {

  private DocumentRegistryBackfill() {}

  public static DocumentRegistry backfill(DocumentRegistry registry, List<Message> messages) {
    final var found = new ArrayList<IngestedDocument>();
    for (int i = 0; i < messages.size(); i++) {
      switch (messages.get(i)) {
        case ToolCallResultMessage toolCallResultMessage -> collect(toolCallResultMessage, found);
        case UserMessage userMessage ->
            collect(userMessage, i > 0 ? messages.get(i - 1) : null, found);
        default -> {
          // system and assistant messages carry no inbound documents
        }
      }
    }
    return registry.withDocuments(found);
  }

  private static void collect(ToolCallResultMessage message, List<IngestedDocument> found) {
    for (final var result : message.results()) {
      final var source =
          DocumentSource.toolCall(result.name(), result.id(), message.id().toString());
      for (final var content : result.content()) {
        documentsOf(content).stream()
            .filter(DocumentRegistryBackfill::isBackfilled)
            .forEach(document -> found.add(new IngestedDocument(document, source)));
      }
    }
  }

  private static void collect(
      UserMessage message, @Nullable Message previous, List<IngestedDocument> found) {
    final boolean toolCallDocuments =
        Boolean.TRUE.equals(message.metadata().get(UserMessage.METADATA_TOOL_CALL_DOCUMENTS));
    final var messageId = message.id().toString();
    final var content = message.content();
    boolean afterEventPreamble = false;
    for (int i = 0; i < content.size(); i++) {
      final var current = content.get(i);
      if (current instanceof TextContent text
          && AgentConversationTurnInputComposerImpl.EVENT_DOCUMENTS_PREAMBLE.equals(text.text())) {
        afterEventPreamble = true;
      } else if (current instanceof DocumentContent documentContent
          && isBackfilled(documentContent.document())) {
        final DocumentSource source;
        if (toolCallDocuments) {
          final var tag = i > 0 ? content.get(i - 1) : null;
          final var attributes =
              tag instanceof TextContent text
                  ? DocumentReferenceXmlTag.parseAttributes(text.text())
                  : null;
          source =
              DocumentSource.toolCall(
                  attributes == null ? null : attributes.get("toolName"),
                  attributes == null ? null : attributes.get("toolCallId"),
                  previous instanceof ToolCallResultMessage toolCallResult
                      ? toolCallResult.id().toString()
                      : null);
        } else if (afterEventPreamble) {
          source = DocumentSource.event(messageId);
        } else {
          source = DocumentSource.userMessage(messageId);
        }
        found.add(new IngestedDocument(documentContent.document(), source));
      }
    }
  }

  private static List<Document> documentsOf(Content content) {
    return switch (content) {
      case DocumentContent documentContent -> List.of(documentContent.document());
      case ObjectContent objectContent ->
          ContentTreeDocumentWalker.extractDocumentsFromContent(objectContent.content());
      default -> List.of();
    };
  }

  private static boolean isBackfilled(Document document) {
    return document.reference() instanceof CamundaDocumentReference
        || document.reference() instanceof ExternalDocumentReference;
  }
}
