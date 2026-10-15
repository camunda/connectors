/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;
import java.util.Locale;
import org.apache.hc.core5.http.ContentType;
import org.jspecify.annotations.Nullable;

/**
 * MIME-type primitives shared by every provider's document content-type classification: the parts
 * that are byte-identical everywhere. How a provider buckets a document from there - which native
 * block/part shape a MIME type maps to - differs per provider and stays in that provider's own
 * classifier.
 */
public final class DocumentMimeTypes {

  private static final List<ContentType> IMAGE_CONTENT_TYPES =
      List.of(
          ContentType.IMAGE_JPEG,
          ContentType.IMAGE_PNG,
          ContentType.IMAGE_GIF,
          ContentType.IMAGE_WEBP);

  private static final List<ContentType> TEXT_CONTENT_TYPES =
      List.of(
          ContentType.APPLICATION_JSON,
          ContentType.APPLICATION_XML,
          ContentType.create("application/yaml"),
          ContentType.create("application/x-yaml"));

  private DocumentMimeTypes() {}

  /**
   * A document's content type. Throws a {@link ConnectorException} if it's unset or blank, naming
   * the actual problem instead of failing classification on a made-up value later.
   */
  public static String requireContentType(Document document) {
    final var metadata = document.metadata();
    final var type = metadata != null ? metadata.getContentType() : null;
    if (type == null || type.isBlank()) {
      throw new ConnectorException(
          ERROR_CODE_FAILED_MODEL_CALL,
          "Content type is unset for document with reference '%s'".formatted(document.reference()));
    }
    return type;
  }

  /** Parses a raw content-type header, or returns {@code null} if it is blank or unparseable. */
  public static @Nullable ContentType parse(String contentType) {
    if (contentType.isBlank()) {
      return null;
    }
    try {
      return ContentType.parse(contentType.trim().toLowerCase(Locale.ROOT));
    } catch (RuntimeException e) {
      return null;
    }
  }

  /**
   * Whether {@code contentType} is one of the natively supported image formats. Matches via {@link
   * ContentType#isSameMimeType}, so this is safe to call with a {@link ContentType} obtained any
   * way, not just via {@link #parse}.
   */
  public static boolean isImage(ContentType contentType) {
    return IMAGE_CONTENT_TYPES.stream().anyMatch(contentType::isSameMimeType);
  }

  /**
   * Whether {@code contentType} is PDF. Same {@link ContentType#isSameMimeType} matching as {@link
   * #isImage}.
   */
  public static boolean isPdf(ContentType contentType) {
    return contentType.isSameMimeType(ContentType.APPLICATION_PDF);
  }

  /**
   * Whether {@code contentType} should be treated as text: {@code text/*}, JSON/XML/YAML, or a
   * structured syntax suffix ({@code +json}/{@code +xml}/{@code +yaml}) that {@link
   * ContentType#isSameMimeType} can't catch on its own.
   */
  public static boolean isTextIsh(ContentType contentType) {
    final var mime = contentType.getMimeType().toLowerCase(Locale.ROOT);
    return mime.startsWith("text/")
        || TEXT_CONTENT_TYPES.stream().anyMatch(contentType::isSameMimeType)
        || mime.endsWith("+json")
        || mime.endsWith("+xml")
        || mime.endsWith("+yaml");
  }
}
