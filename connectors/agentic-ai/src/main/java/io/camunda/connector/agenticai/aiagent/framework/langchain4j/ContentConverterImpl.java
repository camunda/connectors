/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.document.BinaryDataToContentConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.document.DocumentToContentConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.document.DocumentToContentModule;
import io.camunda.connector.agenticai.model.message.content.BlobContent;
import io.camunda.connector.agenticai.model.message.content.Content;
import io.camunda.connector.agenticai.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.model.message.content.EmbeddedResourceBlobDocumentContent;
import io.camunda.connector.agenticai.model.message.content.EmbeddedResourceContent;
import io.camunda.connector.agenticai.model.message.content.EmbeddedResourceContent.BlobResource;
import io.camunda.connector.agenticai.model.message.content.EmbeddedResourceContent.TextResource;
import io.camunda.connector.agenticai.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.model.message.content.ResourceLinkContent;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import io.camunda.connector.agenticai.util.ObjectMapperConstants;
import java.util.Base64;
import java.util.Optional;

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
      case BlobContent blobContent -> convertBinaryContent(blobContent);
      case EmbeddedResourceContent embeddedResourceContent ->
          convertEmbeddedResourceContent(embeddedResourceContent);
      case ResourceLinkContent resourceLinkContent ->
          new dev.langchain4j.data.message.TextContent(
              convertToString(
                  contentObjectMapper.convertValue(
                      resourceLinkContent,
                      ObjectMapperConstants.STRING_OBJECT_MAP_TYPE_REFERENCE)));
    };
  }

  private dev.langchain4j.data.message.Content convertBinaryContent(BlobContent blobContent) {
    if (blobContent.mimeType() == null) {
      return new dev.langchain4j.data.message.TextContent(
          Base64.getEncoder().encodeToString(blobContent.blob()));
    }

    return Optional.ofNullable(
            BinaryDataToContentConverter.convertFromData(
                blobContent.blob(), blobContent.mimeType()))
        .orElseGet(
            () ->
                new dev.langchain4j.data.message.TextContent(
                    Base64.getEncoder().encodeToString(blobContent.blob())));
  }

  private dev.langchain4j.data.message.Content convertEmbeddedResourceContent(
      EmbeddedResourceContent embeddedResourceContent) {
    return switch (embeddedResourceContent.resource()) {
      case TextResource textResource ->
          new dev.langchain4j.data.message.TextContent(textResource.text());
      case BlobResource blobResource -> convertBlobResource(blobResource);
      case EmbeddedResourceBlobDocumentContent documentContent ->
          documentToContentConverter.convert(documentContent.document());
    };
  }

  private dev.langchain4j.data.message.Content convertBlobResource(BlobResource blobResource) {
    if (blobResource.mimeType() == null) {
      return new dev.langchain4j.data.message.TextContent(
          Base64.getEncoder().encodeToString(blobResource.blob()));
    }

    return Optional.ofNullable(
            BinaryDataToContentConverter.convertFromData(
                blobResource.blob(), blobResource.mimeType()))
        .orElseGet(
            () ->
                new dev.langchain4j.data.message.TextContent(
                    Base64.getEncoder().encodeToString(blobResource.blob())));
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
