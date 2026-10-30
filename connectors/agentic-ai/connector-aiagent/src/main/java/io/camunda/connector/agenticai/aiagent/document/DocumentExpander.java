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
import io.camunda.connector.agenticai.aiagent.conversation.AgentConversation;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistry;
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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns the persisted {@link DocumentReferenceContent document references} in user messages and
 * tool results into the form the model providers and the agent instance history see.
 *
 * <p>The persisted form of a document is its reference only; the document is read from the {@link
 * DocumentRegistry}. In a user message the model gets the tag text of every reference and the
 * document content of the references with {@link DocumentDelivery#CONTENT} ({@link
 * #expandMessages}); the history shows the document of every reference ({@link #expandContent}). A
 * document the registry does not know, or whose reference cannot be read, leaves the tag text
 * alone. A tag a user typed into a prompt is text and stays text.
 *
 * <p>In a tool result a reference becomes the tag text of the document read from the registry, or
 * the tag text of the reference when the registry cannot supply the document. The delivery of such
 * a reference is not read: the model gets a document of a tool result as its tag only. A document
 * nested in an object is replaced by its tag in the object, in a tool result as well as in a user
 * message.
 *
 * <p>The result depends only on the messages and the registry, never on time or call order, so it
 * is stable across requests (prompt caching). Nothing that is stored is changed.
 */
public final class DocumentExpander {

  private static final Logger LOGGER = LoggerFactory.getLogger(DocumentExpander.class);

  private static final String COLLECTED_DOCUMENTS = "collectedDocuments";

  private final ObjectMapper objectMapper;
  private final ObjectMapper taggingMapper;

  /**
   * @param objectMapper the mapper that binds a document reference to a {@link Document}, the one
   *     that reads the documents of persisted messages
   */
  public DocumentExpander(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.taggingMapper =
        objectMapper.copy().registerModule(new SimpleModule().addSerializer(new TagCollector()));
  }

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
  public TaggedObject renderWithTags(Object value) {
    final var documents = new ArrayList<Document>();
    try (var buffer = new TokenBuffer(taggingMapper, false)) {
      taggingMapper
          .writer()
          .withAttribute(COLLECTED_DOCUMENTS, documents)
          .writeValue(buffer, value);
      final Object tree = taggingMapper.readValue(buffer.asParser(), Object.class);
      return new TaggedObject(tree, List.copyOf(documents));
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Failed to render the object with document tags", e);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** The document of the registry entry with the given id; empty when unknown or unreadable. */
  public Optional<Document> findDocument(DocumentRegistry registry, DocumentId id) {
    return Optional.ofNullable(rehydrate(registry, id));
  }

  /**
   * The decorator for the windowed messages of the conversation. For an agent instance with the
   * documents feature it expands the messages as {@link #expandMessages} does; for one without, the
   * messages are left as they are.
   */
  public UnaryOperator<List<Message>> windowDecorator(AgentConversation conversation) {
    if (!conversation.persistedContext().supports(AgentFeatures.DOCUMENTS)) {
      return UnaryOperator.identity();
    }
    final var registry = conversation.documentRegistry();
    return windowed -> expandMessages(windowed, registry);
  }

  /**
   * Returns the messages expanded for the model: in a user message the tag text of every document
   * reference, followed by the document for the references with the delivery {@code content}; in a
   * tool result the tag text of every reference, the tag of the document of the registry or the one
   * of the reference when the registry has no readable document. In both, a document nested in an
   * object is replaced by its tag.
   */
  // Identity check detects that nothing changed, so the input is returned as is
  @SuppressWarnings("ReferenceEquality")
  public List<Message> expandMessages(List<Message> messages, DocumentRegistry registry) {
    final var expanded = new ArrayList<Message>(messages.size());
    boolean changed = false;
    for (final var message : messages) {
      if (message instanceof UserMessage userMessage) {
        final var content = expand(userMessage.content(), registry, true);
        if (content != userMessage.content()) {
          expanded.add(userMessage.withContent(content));
          changed = true;
          continue;
        }
      } else if (message instanceof ToolCallResultMessage toolCallResultMessage) {
        final var results = expandResults(toolCallResultMessage.results(), registry);
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
  public List<Content> expandContent(List<Content> content, DocumentRegistry registry) {
    return expand(content, registry, false);
  }

  // Identity check detects that nothing changed, so the input is returned as is
  @SuppressWarnings("ReferenceEquality")
  private List<Content> expand(List<Content> content, DocumentRegistry registry, boolean forModel) {
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
        final var document = rehydrate(registry, reference.id());
        if (document != null) {
          expanded.add(DocumentContent.documentContent(document));
        }
      }
    }
    return changed ? List.copyOf(expanded) : content;
  }

  // Identity check detects that nothing changed, so the input is returned as is
  @SuppressWarnings("ReferenceEquality")
  private List<ToolCallResultContent> expandResults(
      List<ToolCallResultContent> results, DocumentRegistry registry) {
    final var expanded = new ArrayList<ToolCallResultContent>(results.size());
    boolean changed = false;
    for (final var result : results) {
      final var content = expandResultContent(result.content(), registry);
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
  private List<Content> expandResultContent(List<Content> content, DocumentRegistry registry) {
    final var expanded = new ArrayList<Content>(content.size());
    boolean changed = false;
    for (final var current : content) {
      switch (current) {
        case DocumentReferenceContent reference -> {
          final var document = rehydrate(registry, reference.id());
          // the short tag of the document, without source, tool name and tool call id
          final var tag = document != null ? DocumentReferences.from(document) : reference;
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
  private ObjectContent renderObject(ObjectContent objectContent) {
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

  private @Nullable Document rehydrate(DocumentRegistry registry, DocumentId id) {
    final var entry = registry.findById(id);
    if (entry.isEmpty()) {
      LOGGER.debug("Document {} is not in the registry, leaving its tag without content", id);
      return null;
    }
    try {
      return entry.get().toDocument(objectMapper);
    } catch (RuntimeException e) {
      LOGGER.warn(
          "Document {} could not be read from its registry reference, leaving its tag without content",
          id,
          e);
      return null;
    }
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
