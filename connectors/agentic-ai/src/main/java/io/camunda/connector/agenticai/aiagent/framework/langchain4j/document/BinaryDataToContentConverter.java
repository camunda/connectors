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
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.http.ContentType;

/**
 * Utility class for converting binary data to Langchain4j {@link Content} objects based on MIME
 * type.
 *
 * <p>This class provides shared logic for converting byte arrays with known content types to the
 * appropriate Langchain4j content representation (TextContent, PdfFileContent, ImageContent).
 */
public final class BinaryDataToContentConverter {

  private static final List<ContentType> ADDITIONAL_TEXT_CONTENT_TYPES =
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

  private BinaryDataToContentConverter() {}

  /**
   * Converts a Camunda {@link Document} to a Langchain4j {@link Content} object based on its
   * content type.
   *
   * @param camundaDocument the Camunda document to convert
   * @return a {@link ConvertedContent} containing the converted content and its detected content
   *     type
   */
  public static ConvertedContent convert(Document camundaDocument) {
    final var detectedContentType = getContentType(camundaDocument);
    var targetContent = convert(camundaDocument.asByteArray(), detectedContentType);

    return new ConvertedContent(targetContent, detectedContentType);
  }

  public static Content convert(byte[] data, ContentType detectedContentType) {
    if (isTextContent(detectedContentType)) {
      return new TextContent(new String(data, StandardCharsets.UTF_8));
    }

    return convertBinaryDataTypes(data, detectedContentType);
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

  private static Content convertBinaryDataTypes(byte[] data, ContentType contentType) {
    if (contentType.isSameMimeType(ContentType.APPLICATION_PDF)) {
      return PdfFileContent.from(
          PdfFile.builder().base64Data(Base64.getEncoder().encodeToString(data)).build());
    }

    if (isImageContent(contentType)) {
      return ImageContent.from(
          Image.builder()
              .mimeType(contentType.getMimeType())
              .base64Data(Base64.getEncoder().encodeToString(data))
              .build(),
          ImageContent.DetailLevel.AUTO);
    }
    return null;
  }

  /**
   * Checks if the content type represents text content.
   *
   * @param contentType the content type to check
   * @return true if the content type is text-based
   */
  private static boolean isTextContent(ContentType contentType) {
    return contentType.getMimeType().startsWith("text/")
        || isCompatibleWithAnyOf(contentType, ADDITIONAL_TEXT_CONTENT_TYPES);
  }

  /**
   * Checks if the content type represents image content.
   *
   * @param contentType the content type to check
   * @return true if the content type is a supported image type
   */
  private static boolean isImageContent(ContentType contentType) {
    return isCompatibleWithAnyOf(contentType, IMAGE_CONTENT_TYPES);
  }

  private static boolean isCompatibleWithAnyOf(
      ContentType contentType, List<ContentType> contentTypes) {
    return contentTypes.stream().anyMatch(contentType::isSameMimeType);
  }

  public record ConvertedContent(Content content, ContentType detectedContentType) {

    public boolean hasContent() {
      return Objects.nonNull(content);
    }
  }
}
