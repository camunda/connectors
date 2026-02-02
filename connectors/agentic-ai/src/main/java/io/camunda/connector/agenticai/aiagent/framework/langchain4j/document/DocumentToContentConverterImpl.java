/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.document;

import dev.langchain4j.data.message.Content;
import io.camunda.connector.api.document.Document;

/**
 * Converts a Camunda {@link Document} to a Langchain4j {@link Content} object to be used in user
 * messages.
 *
 * <p>Note: audio and video content types are currently not supported, but can be easily added by
 * following the existing pattern.
 */
public class DocumentToContentConverterImpl implements DocumentToContentConverter {

  @Override
  public Content convert(Document camundaDocument) {
    final var converted = BinaryDataToContentConverter.convert(camundaDocument);
    if (!converted.hasContent()) {
      throw new DocumentConversionException(
          "Unsupported content type '%s' for document with reference '%s'"
              .formatted(converted.detectedContentType(), camundaDocument.reference()));
    }

    return converted.content();
  }
}
