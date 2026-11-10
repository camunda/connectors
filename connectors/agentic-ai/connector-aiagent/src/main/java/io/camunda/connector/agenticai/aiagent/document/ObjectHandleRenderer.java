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
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentHandle;
import io.camunda.connector.api.document.Document;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders an object with every document at any depth replaced by its {@link AgentDocumentHandle},
 * the way the model reads the object of a tool result and of a user message, and reports the
 * documents it replaced.
 */
public class ObjectHandleRenderer {

  private static final String COLLECTED_DOCUMENTS = "collectedDocuments";
  private static final String COLLECT_ONLY = "collectOnly";

  /**
   * Reads the rendered object back as plain JSON values. It has no modules: the data of the object
   * is never interpreted, such as a map that looks like a document reference or an intrinsic
   * function.
   */
  private static final ObjectMapper PLAIN_READER = JsonMapper.builder().build();

  private final ObjectMapper handleMapper;

  /**
   * @param objectMapper the mapper that writes the object, the one that serializes the messages
   */
  public ObjectHandleRenderer(ObjectMapper objectMapper) {
    this.handleMapper =
        objectMapper.copy().registerModule(new SimpleModule().addSerializer(new HandleCollector()));
  }

  /**
   * An object with every document at any depth replaced by its handle, and the documents that were
   * replaced, in order of appearance.
   *
   * @param tree the object as plain JSON values (maps, lists, strings, numbers)
   * @param documents the documents the handles stand for, one entry per occurrence
   */
  public record RenderedObject(Object tree, List<Document> documents) {}

  /**
   * The documents that {@link #render(Object)} replaces with their handles, in order of appearance,
   * one entry per occurrence. No handle is built, so a document whose reference the agent does not
   * support is reported too, for the caller to reject.
   */
  public List<Document> documentsIn(Object value) {
    final var documents = new ArrayList<Document>();
    try (var buffer = new TokenBuffer(handleMapper, false)) {
      handleMapper
          .writer()
          .withAttribute(COLLECTED_DOCUMENTS, documents)
          .withAttribute(COLLECT_ONLY, true)
          .writeValue(buffer, value);
      return List.copyOf(documents);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Failed to collect the documents of the object", e);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Renders the object with a {@link Document} at any depth as its handle object (see {@link
   * AgentDocumentHandle#tree()}), and returns the rendered tree with the documents it replaced.
   */
  public RenderedObject render(Object value) {
    final var documents = new ArrayList<Document>();
    try (var buffer = new TokenBuffer(handleMapper, false)) {
      handleMapper.writer().withAttribute(COLLECTED_DOCUMENTS, documents).writeValue(buffer, value);
      final Object tree = PLAIN_READER.readValue(buffer.asParser(), Object.class);
      return new RenderedObject(tree, List.copyOf(documents));
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Failed to render the object with document handles", e);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static final class HandleCollector extends JsonSerializer<Document> {

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
      if (Boolean.TRUE.equals(provider.getAttribute(COLLECT_ONLY))) {
        gen.writeNull();
        return;
      }
      gen.writeTree(AgentDocumentHandle.of(document).tree());
    }
  }
}
