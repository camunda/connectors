/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.document;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import io.camunda.connector.agenticai.model.message.DocumentReferenceXmlTag;
import io.camunda.connector.api.document.Document;
import java.io.IOException;

/**
 * Jackson serializer that renders a {@link Document} as a {@code <doc/>} XML tag string (Site 1: no
 * tool attribution). Register this via a {@link com.fasterxml.jackson.databind.module.SimpleModule}
 * to have Jackson automatically handle {@link Document} nodes at any nesting depth during JSON
 * serialization.
 */
public class DocumentReferenceTagSerializer extends JsonSerializer<Document> {

  @Override
  public void serialize(Document document, JsonGenerator gen, SerializerProvider provider)
      throws IOException {
    gen.writeString(DocumentReferenceXmlTag.from(document).toXml());
  }
}
