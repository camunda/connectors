/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.document;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.pdf.PdfFile;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentMetadata;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.http.ContentType;

/**
 * Converts a Camunda {@link Document} to a Langchain4j {@link Content} object to be used in user
 * messages.
 *
 * <p>Note: audio and video content types are currently not supported, but can be easily added by
 * following the existing pattern.
 */
public class DocumentToContentConverterImpl implements DocumentToContentConverter {

  private static final List<ContentType> ADDITIONAL_TEXT_FILE_CONTENT_TYPES =
      List.of(
          ContentType.APPLICATION_XML,
          ContentType.APPLICATION_JSON,
          ContentType.create("application/yaml"));

  private static final List<ContentType> IMAGE_CONTENT_TYPES =
      List.of(
          ContentType.IMAGE_JPEG,
          ContentType.IMAGE_PNG,
          ContentType.IMAGE_GIF,
          ContentType.IMAGE_WEBP);

  @Override
  public Content convert(Document camundaDocument) {
    final var contentType = getContentType(camundaDocument);

    if (contentType.getMimeType().startsWith("text/")
        || isCompatibleWithAnyOf(contentType, ADDITIONAL_TEXT_FILE_CONTENT_TYPES)) {
      return new TextContent(new String(camundaDocument.asByteArray(), StandardCharsets.UTF_8));
    }

    if (contentType.isSameMimeType(ContentType.APPLICATION_PDF)) {
      return PdfFileContent.from(PdfFile.builder().base64Data(camundaDocument.asBase64()).build());
    }

    if (isCompatibleWithAnyOf(contentType, IMAGE_CONTENT_TYPES)) {
      return ImageContent.from(
          Image.builder()
              .mimeType(contentType.getMimeType())
              .base64Data(camundaDocument.asBase64())
              .build(),
          ImageContent.DetailLevel.AUTO);
    }

    throw new DocumentConversionException(
        "Unsupported content type '%s' for document with reference '%s'"
            .formatted(contentType, camundaDocument.reference()));
  }

  private static ContentType getContentType(Document camundaDocument) {
    return Optional.ofNullable(camundaDocument.metadata())
        .map(DocumentMetadata::getContentType)
        .filter(StringUtils::isNotBlank)
        .map(ContentType::parse)
        .orElseThrow(
            () ->
                new DocumentConversionException(
                    "Content type is unset for document with reference '%s'"
                        .formatted(camundaDocument.reference())));
  }

  private boolean isCompatibleWithAnyOf(ContentType contentType, List<ContentType> contentTypes) {
    return contentTypes.stream().anyMatch(contentType::isSameMimeType);
  }
}
