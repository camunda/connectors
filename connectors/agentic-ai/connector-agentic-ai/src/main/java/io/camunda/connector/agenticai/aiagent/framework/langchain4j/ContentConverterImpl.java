/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.document.DocumentToContentConverter;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public class ContentConverterImpl implements ContentConverter {
  private final DocumentToContentConverter documentToContentConverter;
  private final ObjectMapper objectMapper;

  public ContentConverterImpl(
      ObjectMapper objectMapper, DocumentToContentConverter documentToContentConverter) {
    this.documentToContentConverter = documentToContentConverter;
    this.objectMapper = objectMapper;
  }

  @Override
  public dev.langchain4j.data.message.Content convertToContent(Content content)
      throws JsonProcessingException {
    return switch (content) {
      case TextContent textContent ->
          new dev.langchain4j.data.message.TextContent(textContent.text());
      case DocumentContent documentContent ->
          documentToContentConverter.convert(documentContent.document());
      case ObjectContent objectContent ->
          new dev.langchain4j.data.message.TextContent(
              Objects.requireNonNull(convertToString(objectContent.content())));
      case ReasoningContent reasoningContent ->
          // LangChain4j 1.17.2 has no per-block content representation for reasoning: its only
          // hook is a flat, @Experimental, message-level AiMessage.thinking()/attributes()
          // string, which cannot carry this content's opaque provider payload and metadata
          // losslessly. Routing reasoning through this framework is therefore unsupported.
          throw new UnsupportedOperationException(
              "Reasoning content is not supported by the LangChain4J framework abstraction");
      case ProviderContent providerContent ->
          // LangChain4j 1.17.2 has no representation for provider-opaque content blocks (no
          // equivalent to a provider/blockType/payload triple), so they cannot be round-tripped
          // through this framework.
          throw new UnsupportedOperationException(
              "Provider content is not supported by the LangChain4J framework abstraction");
    };
  }

  @Override
  public @Nullable String convertToString(@Nullable Object content) throws JsonProcessingException {
    if (content == null) {
      return null;
    }

    if (content instanceof String stringContent) {
      return stringContent;
    }

    return objectMapper.writeValueAsString(content);
  }
}
