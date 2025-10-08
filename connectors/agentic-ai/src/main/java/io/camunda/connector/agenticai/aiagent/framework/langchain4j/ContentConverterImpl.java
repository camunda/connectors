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
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.document.DocumentToContentModule;
import io.camunda.connector.agenticai.model.message.content.Content;
import io.camunda.connector.agenticai.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.model.message.content.TextContent;

public class ContentConverterImpl implements ContentConverter {
  private final DocumentToContentConverter documentToContentConverter;
  private final ObjectMapper contentObjectMapper;

  public ContentConverterImpl(
      ObjectMapper objectMapper, DocumentToContentConverter documentToContentConverter) {
    this.documentToContentConverter = documentToContentConverter;
    this.contentObjectMapper =
        objectMapper.copy().registerModule(new DocumentToContentModule(documentToContentConverter));
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
          new dev.langchain4j.data.message.TextContent(convertToString(objectContent.content()));
    };
  }

  @Override
  public String convertToString(Object content) throws JsonProcessingException {
    if (content == null) {
      return null;
    }

    if (content instanceof String stringContent) {
      return stringContent;
    }

    return contentObjectMapper.writeValueAsString(content);
  }
}
