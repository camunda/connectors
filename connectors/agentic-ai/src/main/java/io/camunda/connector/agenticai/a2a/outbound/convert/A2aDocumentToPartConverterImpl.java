/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.outbound.convert;

import static io.camunda.connector.agenticai.util.JacksonExceptionMessageExtractor.humanReadableJsonProcessingExceptionMessage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.a2a.spec.DataPart;
import io.a2a.spec.FilePart;
import io.a2a.spec.FileWithBytes;
import io.a2a.spec.Part;
import io.a2a.spec.TextPart;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.document.DocumentConversionException;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.document.DocumentToContentConverterImpl;
import io.camunda.connector.agenticai.util.ObjectMapperConstants;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.error.ConnectorException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.http.ContentType;

/**
 * Converts a Camunda {@link Document} to an A2A {@link Part} object to be used in messages. This
 * class is very similar to {@link DocumentToContentConverterImpl}
 *
 * <p>Note: audio and video content types are currently not supported.
 */
public class A2aDocumentToPartConverterImpl implements A2aDocumentToPartConverter {

  private final ObjectMapper objectMapper;

  private static final List<ContentType> ADDITIONAL_TEXT_FILE_CONTENT_TYPES =
      List.of(ContentType.APPLICATION_XML, ContentType.create("application/yaml"));

  private static final List<ContentType> PDF_AND_IMAGE_CONTENT_TYPES =
      List.of(
          ContentType.APPLICATION_PDF,
          ContentType.IMAGE_JPEG,
          ContentType.IMAGE_PNG,
          ContentType.IMAGE_GIF,
          ContentType.IMAGE_WEBP);

  public A2aDocumentToPartConverterImpl(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public Part<?> convert(Document camundaDocument) {
    final var contentType = getContentType(camundaDocument);

    if (contentType.isSameMimeType(ContentType.APPLICATION_JSON)) {
      return createDataPart(camundaDocument, contentType);
    }

    if (contentType.getMimeType().startsWith("text/")
        || isCompatibleWithAnyOf(contentType, ADDITIONAL_TEXT_FILE_CONTENT_TYPES)) {
      return new TextPart(
          new String(camundaDocument.asByteArray(), StandardCharsets.UTF_8),
          Map.of("contentType", contentType));
    }

    if (isCompatibleWithAnyOf(contentType, PDF_AND_IMAGE_CONTENT_TYPES)) {
      FileWithBytes fileWithBytes =
          new FileWithBytes(
              contentType.getMimeType(),
              camundaDocument.metadata().getFileName(),
              camundaDocument.asBase64());
      return new FilePart(fileWithBytes);
    }

    throw new DocumentConversionException(
        "Unsupported content type '%s' for document with reference '%s'"
            .formatted(contentType, camundaDocument.reference()));
  }

  private DataPart createDataPart(Document camundaDocument, ContentType contentType) {
    try {
      Map<String, Object> data =
          objectMapper.readValue(
              camundaDocument.asByteArray(),
              ObjectMapperConstants.STRING_OBJECT_MAP_TYPE_REFERENCE);
      return new DataPart(data, Map.of("contentType", contentType));
    } catch (JsonProcessingException e) {
      throw new ConnectorException(
          "Could not convert document with reference %s to JSON: %s"
              .formatted(
                  camundaDocument.reference(), humanReadableJsonProcessingExceptionMessage(e)));
    } catch (IOException e) {
      throw new ConnectorException(
          "Could not convert document with reference %s to JSON: %s"
              .formatted(camundaDocument, e.getMessage()));
    }
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
