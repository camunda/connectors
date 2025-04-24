/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.TextFileContent;
import dev.langchain4j.data.pdf.PdfFile;
import dev.langchain4j.data.text.TextFile;
import io.camunda.document.Document;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;

public class CamundaDocumentToContentConverter {

  private static final MediaType TEXT_WILDCARD_MIME_TYPE = new MediaType("text");

  private static final List<MediaType> ADDITIONAL_TEXT_FILE_MIME_TYPES =
      List.of(MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON, MediaType.APPLICATION_YAML);

  private static final List<MediaType> IMAGE_MIME_TYPES =
      List.of(
          MediaType.IMAGE_JPEG,
          MediaType.IMAGE_PNG,
          MediaType.IMAGE_GIF,
          MediaType.parseMediaType("image/webp"));

  public List<Content> convert(List<Document> camundaDocuments) {
    return camundaDocuments.stream().map(this::convert).toList();
  }

  public Content convert(Document camundaDocument) {
    final var mediaType =
        Optional.ofNullable(camundaDocument.metadata().getContentType())
            .filter(StringUtils::isNotBlank)
            .map(MediaType::parseMediaType)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Content type is unset. Document reference: %s"
                            .formatted(camundaDocument.reference())));

    if (mediaType.isCompatibleWith(MediaType.TEXT_PLAIN)) {
      return new TextContent(new String(camundaDocument.asByteArray()));
    }

    if (mediaType.isCompatibleWith(TEXT_WILDCARD_MIME_TYPE)
        || isCompatibleWithAnyOf(mediaType, ADDITIONAL_TEXT_FILE_MIME_TYPES)) {
      return TextFileContent.from(
          TextFile.builder()
              .mimeType(mediaType.toString())
              .base64Data(camundaDocument.asBase64())
              .build());
    }

    if (mediaType.isCompatibleWith(MediaType.APPLICATION_PDF)) {
      return PdfFileContent.from(PdfFile.builder().base64Data(camundaDocument.asBase64()).build());
    }

    if (isCompatibleWithAnyOf(mediaType, IMAGE_MIME_TYPES)) {
      return ImageContent.from(
          Image.builder()
              .mimeType(mediaType.toString())
              .base64Data(camundaDocument.asBase64())
              .build());
    }

    throw new IllegalArgumentException(
        "Unsupported content type '%s'. Document reference: %s"
            .formatted(mediaType, camundaDocument.reference()));
  }

  private boolean isCompatibleWithAnyOf(MediaType mediaType, List<MediaType> mimeTypes) {
    return mimeTypes.stream().anyMatch(mediaType::isCompatibleWith);
  }
}
