/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import io.camunda.connector.agenticai.aiagent.agent.ContentTreeDocumentWalker;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentReferenceContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.text.StringEscapeUtils;

/**
 * Builds the transient note that tells the model about registry documents whose messages are no
 * longer part of the windowed conversation.
 *
 * <p>A registry document counts as visible when its id appears in a windowed user or tool result
 * message, as {@link DocumentContent}, as a {@link DocumentReferenceContent}, as a {@link
 * io.camunda.connector.api.document.Document} nested in object content, or as the id of a {@code
 * <doc/>} tag in text, or when the message that introduced it (see {@link
 * DocumentSource#messageId()}) is windowed. The note is added to the snapshot sent to the model
 * only; it is never part of the stored conversation.
 */
public final class EvictedDocumentsNote {

  /** Text introducing the listed tags. */
  public static final String PREAMBLE =
      "Documents from earlier messages that are no longer part of this conversation context. "
          + "They are still available by id:";

  private static final Pattern TAG_ID = Pattern.compile("<doc\\s+id=\"([^\"]*)\"");

  private EvictedDocumentsNote() {}

  /**
   * Returns the windowed messages followed by a user message listing the registry documents that
   * are not visible in them, or the unchanged list when there is no such document.
   *
   * <p>The note is not added after an assistant message: a continuation round resumes on the
   * assistant message that ends the conversation.
   */
  public static List<Message> appendTo(List<Message> windowed, DocumentRegistry registry) {
    if (registry.isEmpty()
        || windowed.isEmpty()
        || windowed.getLast() instanceof AssistantMessage) {
      return windowed;
    }

    final var visibleIds = visibleDocumentIds(windowed);
    final var windowedMessageIds =
        windowed.stream().map(message -> message.id().toString()).collect(Collectors.toSet());
    final var evicted =
        registry.entries().stream()
            .filter(entry -> !visibleIds.contains(entry.id()))
            .filter(entry -> !windowedMessageIds.contains(entry.source().messageId()))
            .toList();
    if (evicted.isEmpty()) {
      return windowed;
    }

    final var content = new ArrayList<Content>();
    content.add(TextContent.textContent(PREAMBLE));
    evicted.forEach(
        entry ->
            content.add(TextContent.textContent(DocumentReferenceContent.from(entry).toTag())));

    final var messages = new ArrayList<>(windowed);
    messages.add(UserMessage.builder().content(content).build());
    return List.copyOf(messages);
  }

  private static Set<String> visibleDocumentIds(List<Message> messages) {
    final var ids = new HashSet<String>();
    for (final var message : messages) {
      switch (message) {
        case UserMessage userMessage -> userMessage.content().forEach(c -> collectIds(c, ids));
        case ToolCallResultMessage toolCallResultMessage ->
            toolCallResultMessage
                .results()
                .forEach(result -> result.content().forEach(c -> collectIds(c, ids)));
        default -> {
          // only user and tool result messages carry inbound documents
        }
      }
    }
    return ids;
  }

  private static void collectIds(Content content, Set<String> ids) {
    switch (content) {
      case DocumentContent documentContent ->
          ids.add(DocumentHandle.idFor(documentContent.document()));
      case ObjectContent objectContent ->
          ContentTreeDocumentWalker.extractDocumentsFromContent(objectContent.content())
              .forEach(document -> ids.add(DocumentHandle.idFor(document)));
      case DocumentReferenceContent reference -> ids.add(reference.id());
      case TextContent textContent -> {
        final var matcher = TAG_ID.matcher(textContent.text());
        while (matcher.find()) {
          ids.add(StringEscapeUtils.unescapeXml(matcher.group(1)));
        }
      }
      default -> {
        // reasoning and provider content carry no documents
      }
    }
  }
}
