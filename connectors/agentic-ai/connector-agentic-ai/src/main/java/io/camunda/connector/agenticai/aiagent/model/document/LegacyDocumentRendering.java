/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.api.document.Document;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Marks the documents of the tool results of a conversation to be rendered to the model as their
 * Camunda document reference (see {@link ReferenceRenderedDocument}), the way the tool results of
 * an agent instance created without the documents feature are rendered. Only the view that is sent
 * to the model changes; the messages that are stored are not touched.
 */
public final class LegacyDocumentRendering {

  private LegacyDocumentRendering() {}

  /** Returns the messages with the documents of their tool results marked. */
  public static List<Message> apply(List<Message> messages) {
    final var rendered = new ArrayList<Message>(messages.size());
    boolean changed = false;
    for (final var message : messages) {
      if (message instanceof ToolCallResultMessage toolCallResultMessage) {
        final var results = results(toolCallResultMessage.results());
        if (results != toolCallResultMessage.results()) {
          rendered.add(toolCallResultMessage.withResults(results));
          changed = true;
          continue;
        }
      }
      rendered.add(message);
    }
    return changed ? List.copyOf(rendered) : messages;
  }

  private static List<ToolCallResultContent> results(List<ToolCallResultContent> results) {
    final var rendered = new ArrayList<ToolCallResultContent>(results.size());
    boolean changed = false;
    for (final var result : results) {
      final var content = content(result.content());
      if (content != result.content()) {
        rendered.add(result.withContent(content));
        changed = true;
      } else {
        rendered.add(result);
      }
    }
    return changed ? List.copyOf(rendered) : results;
  }

  private static List<Content> content(List<Content> contents) {
    final var rendered = new ArrayList<Content>(contents.size());
    boolean changed = false;
    for (final var content : contents) {
      switch (content) {
        case DocumentContent documentContent
            when !(documentContent.document() instanceof ReferenceRenderedDocument) -> {
          rendered.add(
              new DocumentContent(
                  new ReferenceRenderedDocument(documentContent.document()),
                  documentContent.metadata()));
          changed = true;
        }
        case ObjectContent objectContent -> {
          final var node = node(objectContent.content());
          if (node != objectContent.content()) {
            rendered.add(new ObjectContent(Objects.requireNonNull(node), objectContent.metadata()));
            changed = true;
          } else {
            rendered.add(content);
          }
        }
        default -> rendered.add(content);
      }
    }
    return changed ? List.copyOf(rendered) : contents;
  }

  /** Returns the node with its documents marked, or the same node when it holds none. */
  private static @Nullable Object node(@Nullable Object node) {
    return switch (node) {
      case ReferenceRenderedDocument rendered -> rendered;
      case Document document -> new ReferenceRenderedDocument(document);
      case Map<?, ?> map -> {
        final var rendered = new LinkedHashMap<Object, Object>();
        boolean changed = false;
        for (final var entry : map.entrySet()) {
          final var value = node(entry.getValue());
          changed |= value != entry.getValue();
          rendered.put(entry.getKey(), value);
        }
        yield changed ? rendered : map;
      }
      case List<?> list -> {
        final var rendered = new ArrayList<@Nullable Object>(list.size());
        boolean changed = false;
        for (final var element : list) {
          final var value = node(element);
          changed |= value != element;
          rendered.add(value);
        }
        yield changed ? rendered : list;
      }
      case Object[] array -> {
        final var rendered = new Object[array.length];
        boolean changed = false;
        for (int i = 0; i < array.length; i++) {
          rendered[i] = node(array[i]);
          changed |= rendered[i] != array[i];
        }
        yield changed ? rendered : array;
      }
      case null, default -> node;
    };
  }
}
