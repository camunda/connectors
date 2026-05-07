/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.content;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.model.message.content.Content;
import io.camunda.connector.agenticai.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.model.message.content.TextContent;

/**
 * Renders a {@link Content} block to a text representation suitable for provider-native APIs that
 * only accept text (system prompts, assistant content, the text portion of multimodal user
 * messages, the text fallback for unsupported tool-result modalities).
 *
 * <p>Mirrors the LangChain4j bridge's {@code ContentConverterImpl}: a {@link TextContent} returns
 * its text verbatim; an {@link ObjectContent} returns its inner value as-is when it's already a
 * {@code String}, otherwise as Jackson-serialised JSON. Other content types must be handled by the
 * caller before reaching this helper.
 *
 * <p>Callers are expected to pass the {@code @ConnectorsObjectMapper} so nested {@link
 * io.camunda.connector.api.document.Document}s serialise to their reference shape rather than
 * throwing.
 */
public final class ContentTextSerializer {

  private ContentTextSerializer() {}

  public static String toText(Content content, ObjectMapper objectMapper) {
    if (content instanceof TextContent text) {
      return text.text();
    }
    if (content instanceof ObjectContent object) {
      return objectContentToText(object, objectMapper);
    }
    throw new IllegalArgumentException(
        "Unsupported content block for text serialization: " + content.getClass().getSimpleName());
  }

  public static String objectContentToText(ObjectContent content, ObjectMapper objectMapper) {
    final var value = content.content();
    if (value instanceof String s) {
      return s;
    }
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize ObjectContent value to JSON", e);
    }
  }
}
