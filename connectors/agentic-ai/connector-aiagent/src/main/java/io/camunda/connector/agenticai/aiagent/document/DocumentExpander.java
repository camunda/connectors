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
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentId;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentReferenceContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.api.document.DocumentReference.ExternalDocumentReference;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Turns the persisted {@link DocumentReferenceContent document references} in user messages and
 * tool results into the form the model providers and the agent instance history see.
 *
 * <p>The persisted form of a document is its reference, which holds the document. In a user message
 * the model gets the tag text of every reference and the document content of the references with
 * {@link DocumentDelivery#CONTENT} ({@link #expandMessages}); the history shows the document of
 * every reference ({@link #expandContent}). A tag a user typed into a prompt is text and stays
 * text.
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
   * The decorator for the windowed messages of the conversation. For an agent instance with the
   * documents feature it expands the messages as {@link #expandMessages} does; for one without, the
   * messages are left as they are.
   */
  public static UnaryOperator<List<Message>> windowDecorator(AgentContext agentContext) {
    if (!agentContext.supports(AgentFeatures.DOCUMENTS)) {
      return UnaryOperator.identity();
    }
    return DocumentExpander::expandMessages;
  }

  /**
   * Returns the messages expanded for the model: in a user message the tag text of every document
   * reference, followed by the document for the references with the delivery {@code content}; in a
   * tool result the short tag text of every reference. In both, a document nested in an object is
   * replaced by its tag.
   */
  // Identity check detects that nothing changed, so the input is returned as is
  @SuppressWarnings("ReferenceEquality")
  public static List<Message> expandMessages(List<Message> messages) {
    final var expanded = new ArrayList<Message>(messages.size());
    boolean changed = false;
    for (final var message : messages) {
      if (message instanceof UserMessage userMessage) {
        final var content = expand(userMessage.content(), true);
        if (content != userMessage.content()) {
          expanded.add(userMessage.withContent(content));
          changed = true;
          continue;
        }
      } else if (message instanceof ToolCallResultMessage toolCallResultMessage) {
        final var results = expandResults(toolCallResultMessage.results());
        if (results != toolCallResultMessage.results()) {
          expanded.add(toolCallResultMessage.withResults(results));
          changed = true;
          continue;
        }
      }
      expanded.add(message);
    }
    return changed ? List.copyOf(expanded) : messages;
  }

  /**
   * Returns the content expanded to the tag text and the document of every document reference, or
   * the same list when nothing needs to change.
   */
  public static List<Content> expandContent(List<Content> content) {
    return expand(content, false);
  }

  // Identity check detects that nothing changed, so the input is returned as is
  @SuppressWarnings("ReferenceEquality")
  private static List<Content> expand(List<Content> content, boolean forModel) {
    final var expanded = new ArrayList<Content>(content.size());
    boolean changed = false;
    for (int i = 0; i < content.size(); i++) {
      final var current = content.get(i);
      final var next = i + 1 < content.size() ? content.get(i + 1) : null;
      if (forModel && current instanceof ObjectContent objectContent) {
        final var rendered = renderObject(objectContent);
        expanded.add(rendered);
        changed |= rendered != objectContent;
        continue;
      }
      if (!(current instanceof DocumentReferenceContent reference)) {
        expanded.add(current);
        continue;
      }

      expanded.add(new TextContent(reference.toTag(), reference.metadata()));
      changed = true;
      final var withContent = !forModel || reference.delivery() == DocumentDelivery.CONTENT;
      if (withContent
          && !(next instanceof DocumentContent following
              && isDocumentWithId(following.document(), reference.id()))) {
        expanded.add(DocumentContent.documentContent(reference.document()));
      }
    }
    return changed ? List.copyOf(expanded) : content;
  }

  // Identity check detects that nothing changed, so the input is returned as is
  @SuppressWarnings("ReferenceEquality")
  private static List<ToolCallResultContent> expandResults(List<ToolCallResultContent> results) {
    final var expanded = new ArrayList<ToolCallResultContent>(results.size());
    boolean changed = false;
    for (final var result : results) {
      final var content = expandResultContent(result.content());
      if (content != result.content()) {
        expanded.add(result.withContent(content));
        changed = true;
      } else {
        expanded.add(result);
      }
    }
    return changed ? List.copyOf(expanded) : results;
  }

  // Identity check detects that nothing changed, so the input is returned as is
  @SuppressWarnings("ReferenceEquality")
  private static List<Content> expandResultContent(List<Content> content) {
    final var expanded = new ArrayList<Content>(content.size());
    boolean changed = false;
    for (final var current : content) {
      switch (current) {
        case DocumentReferenceContent reference -> {
          // the short tag of the document, without source, tool name and tool call id
          final var tag = DocumentReferences.from(reference.document());
          expanded.add(new TextContent(tag.toTag(), reference.metadata()));
          changed = true;
        }
        case ObjectContent objectContent -> {
          final var rendered = renderObject(objectContent);
          expanded.add(rendered);
          changed |= rendered != objectContent;
        }
        default -> expanded.add(current);
      }
    }
    return changed ? List.copyOf(expanded) : content;
  }

  /** The object with its documents replaced by their tags, or the same object when it has none. */
  private static ObjectContent renderObject(ObjectContent objectContent) {
    final var tagged = renderWithTags(objectContent.content());
    return tagged.documents().isEmpty()
        ? objectContent
        : new ObjectContent(tagged.tree(), objectContent.metadata());
  }

  private static boolean isDocumentWithId(Document document, DocumentId id) {
    return isTaggable(document) && id.equals(DocumentHandle.idFor(document));
  }

  /** Only Camunda and external documents get a tag, inline documents are left as they are. */
  private static boolean isTaggable(Document document) {
    return document.reference() instanceof CamundaDocumentReference
        || document.reference() instanceof ExternalDocumentReference;
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
