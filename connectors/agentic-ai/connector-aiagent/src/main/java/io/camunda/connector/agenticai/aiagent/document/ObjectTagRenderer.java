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
import io.camunda.connector.api.document.Document;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders an object with every document at any depth replaced by its short tag, the way the model
 * reads the object of a tool result and of a user message, and reports the documents it replaced.
 */
public final class ObjectTagRenderer {

  private static final String COLLECTED_DOCUMENTS = "collectedDocuments";

  private final ObjectMapper taggingMapper;

  /**
   * @param objectMapper the mapper that writes the object, the one that serializes the messages
   */
  public ObjectTagRenderer(ObjectMapper objectMapper) {
    this.taggingMapper =
        objectMapper.copy().registerModule(new SimpleModule().addSerializer(new TagCollector()));
  }

  /**
   * An object with every document at any depth replaced by its tag, and the documents that were
   * replaced, in order of appearance.
   *
   * @param tree the object as plain JSON values (maps, lists, strings, numbers)
   * @param documents the documents the tags stand for, one entry per occurrence
   */
  public record TaggedObject(Object tree, List<Document> documents) {}

  /**
   * Renders the object with a {@link Document} at any depth as its tag (see {@link
   * DocumentTags#shortTag(Document)}), and returns the rendered tree with the documents it
   * replaced.
   */
  public TaggedObject render(Object value) {
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
      gen.writeString(DocumentTags.shortTag(document));
    }
  }
}
