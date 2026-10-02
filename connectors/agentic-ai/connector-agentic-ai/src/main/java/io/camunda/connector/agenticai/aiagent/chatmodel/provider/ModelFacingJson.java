/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.camunda.connector.agenticai.aiagent.model.message.DocumentReferenceXmlTag;
import io.camunda.connector.api.document.Document;
import java.io.IOException;
import org.jspecify.annotations.Nullable;

/**
 * Serializes content to JSON text for the model. A {@link Document} at any nesting depth is
 * rendered as its {@code <doc id="..." />} tag (see {@link DocumentReferenceXmlTag}) instead of the
 * Camunda document reference, so a store id, content hash or URL never reaches the model. A {@link
 * Document} as the whole value is returned as the bare tag, not as a JSON string.
 *
 * <p>Every provider path that turns tool result or message object content into text goes through
 * this class; the registry keeps the real references, only the model-facing rendering changes.
 */
public final class ModelFacingJson {

  private final ObjectMapper mapper;

  public ModelFacingJson(ObjectMapper objectMapper) {
    this.mapper =
        objectMapper.copy().registerModule(new SimpleModule().addSerializer(new TagSerializer()));
  }

  /** The mapper that renders documents as tags, for tree conversions of model-facing values. */
  public ObjectMapper mapper() {
    return mapper;
  }

  public String write(@Nullable Object value) throws JsonProcessingException {
    if (value instanceof Document document) {
      return DocumentReferenceXmlTag.from(document).toXml();
    }
    return mapper.writeValueAsString(value);
  }

  private static final class TagSerializer extends JsonSerializer<Document> {

    @Override
    public Class<Document> handledType() {
      return Document.class;
    }

    @Override
    public void serialize(Document document, JsonGenerator gen, SerializerProvider provider)
        throws IOException {
      gen.writeString(DocumentReferenceXmlTag.from(document).toXml());
    }
  }
}
