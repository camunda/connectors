/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentReferenceContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentDelivery;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns the persisted {@link DocumentReferenceContent document references} in user messages and
 * tool results into the form the model providers see.
 *
 * <p>The persisted form of a document is its reference, which holds the document. In a user message
 * the model gets the tag text of every reference and the document content of the references with
 * {@link DocumentDelivery#CONTENT}. A tag a user typed into a prompt is text and stays text.
 *
 * <p>In a tool result a reference becomes the short tag text of its document. The delivery of such
 * a reference is not read: the model gets a document of a tool result as its tag only. A document
 * nested in an object is replaced by its tag in the object, in a tool result as well as in a user
 * message.
 *
 * <p>The result depends only on the messages, never on time or call order, so it is stable across
 * requests (prompt caching). Nothing that is stored is changed.
 */
public final class DocumentExpander {

  private static final String COLLECTED_DOCUMENTS = "collectedDocuments";

  private static final ObjectMapper TAGGING_MAPPER =
      ConnectorsObjectMapperSupplier.getCopy()
          .registerModule(new SimpleModule().addSerializer(new TagCollector()));

  private DocumentExpander() {}

  /**
   * An object with every document at any depth replaced by its tag, as the model reads it, and the
   * documents that were replaced, in order of appearance.
   *
   * @param tree the object as plain JSON values (maps, lists, strings, numbers)
   * @param documents the documents the tags stand for, one entry per occurrence
   */
  public record TaggedObject(Object tree, List<Document> documents) {}

  /**
   * Renders the object the way the model reads it, a {@link Document} at any depth as its tag (see
   * {@link DocumentReferenceContent#toTag()}), and returns the rendered tree with the documents it
   * replaced.
   */
  public static TaggedObject renderWithTags(Object value) {
    final var documents = new ArrayList<Document>();
    try (var buffer = new TokenBuffer(TAGGING_MAPPER, false)) {
      TAGGING_MAPPER
          .writer()
          .withAttribute(COLLECTED_DOCUMENTS, documents)
          .writeValue(buffer, value);
      final Object tree = TAGGING_MAPPER.readValue(buffer.asParser(), Object.class);
      return new TaggedObject(tree, List.copyOf(documents));
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Failed to render the object with document tags", e);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Returns the messages expanded for the model: in a user message the tag text of every document
   * reference, followed by the document for the references with the delivery {@code content}; in a
   * tool result the short tag text of every reference. In both, a document nested in an object is
   * replaced by its tag.
   */
  public static List<Message> expandMessages(List<Message> messages) {
    return messages.stream().map(DocumentExpander::expand).toList();
  }

  private static Message expand(Message message) {
    return switch (message) {
      case UserMessage userMessage ->
          userMessage.withContent(expandUserContent(userMessage.content()));
      case ToolCallResultMessage toolCallResultMessage ->
          toolCallResultMessage.withResults(
              toolCallResultMessage.results().stream()
                  .map(result -> result.withContent(expandResultContent(result.content())))
                  .toList());
      default -> message;
    };
  }

  private static List<Content> expandUserContent(List<Content> content) {
    final var expanded = new ArrayList<Content>(content.size());
    for (final var current : content) {
      switch (current) {
        case DocumentReferenceContent reference -> {
          expanded.add(new TextContent(reference.toTag(), reference.metadata()));
          if (reference.delivery() == DocumentDelivery.CONTENT) {
            expanded.add(DocumentContent.documentContent(reference.document()));
          }
        }
        case ObjectContent objectContent -> expanded.add(renderObject(objectContent));
        default -> expanded.add(current);
      }
    }
    return List.copyOf(expanded);
  }

  private static List<Content> expandResultContent(List<Content> content) {
    return content.stream()
        .map(
            current ->
                switch (current) {
                  // the short tag of the document, without source, tool name and tool call id
                  case DocumentReferenceContent reference ->
                      new TextContent(
                          DocumentReferences.from(reference.document()).toTag(),
                          reference.metadata());
                  case ObjectContent objectContent -> renderObject(objectContent);
                  default -> current;
                })
        .toList();
  }

  /** The object with its documents replaced by their tags, or the same object when it has none. */
  private static ObjectContent renderObject(ObjectContent objectContent) {
    final var tagged = renderWithTags(objectContent.content());
    return tagged.documents().isEmpty()
        ? objectContent
        : new ObjectContent(tagged.tree(), objectContent.metadata());
  }

  private static final class TagCollector extends JsonSerializer<Document> {

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
      gen.writeString(DocumentReferences.from(document).toTag());
    }
  }
}
