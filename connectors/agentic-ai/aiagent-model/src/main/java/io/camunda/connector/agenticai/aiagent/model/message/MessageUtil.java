/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.message;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentId;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentReferenceContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentDelivery;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class MessageUtil {
  private MessageUtil() {}

  private static final NoArgGenerator ID_GENERATOR = Generators.timeBasedEpochGenerator();

  /**
   * Builder default for {@link Message#id()}; also backfills ids on pre-existing persisted data.
   */
  public static MessageId generateId() {
    return MessageId.of(ID_GENERATOR.generate());
  }

  public static List<Content> singleTextContent(String text) {
    return List.of(TextContent.textContent(text));
  }

  public static List<Content> content(Content... contents) {
    return List.of(contents);
  }

  public static Optional<SystemMessage> leadingSystemMessage(List<Message> messages) {
    return !messages.isEmpty() && messages.getFirst() instanceof SystemMessage systemMessage
        ? Optional.of(systemMessage)
        : Optional.empty();
  }

  /**
   * Flattens a {@link ContentMessage}'s {@link TextContent} blocks to a single string, joined with
   * {@code \n}; non-text content blocks are ignored.
   */
  public static String contentText(ContentMessage message) {
    return message.content().stream()
        .filter(TextContent.class::isInstance)
        .map(c -> ((TextContent) c).text())
        .collect(Collectors.joining("\n"));
  }

  /**
   * Returns the ids of the documents whose content the model reads in the given messages: the
   * documents of the references with the delivery {@code content} in the user messages.
   */
  public static Set<DocumentId> documentIdsWithContent(List<Message> messages) {
    final var ids = new LinkedHashSet<DocumentId>();
    for (final var message : messages) {
      if (message instanceof UserMessage userMessage) {
        for (final var content : userMessage.content()) {
          if (content instanceof DocumentReferenceContent reference
              && reference.delivery() == DocumentDelivery.CONTENT) {
            ids.add(reference.id());
          }
        }
      }
    }
    return Set.copyOf(ids);
  }
}
