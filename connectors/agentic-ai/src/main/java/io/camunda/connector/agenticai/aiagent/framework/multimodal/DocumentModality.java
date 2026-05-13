/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.multimodal;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL;

import io.camunda.connector.agenticai.aiagent.framework.api.ModelCapabilities.Modality;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.error.ConnectorException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

/**
 * Maps a Camunda {@link Document}'s declared MIME type to a {@link Modality}.
 *
 * <p>Modality vocabulary matches the capability matrix; the routing strategy uses this to decide
 * whether a document fits the resolved model's modality slot, and the per-impl emitters use it to
 * dispatch construction of provider-native content blocks.
 *
 * <p>Coverage parity with the LangChain4j path ({@code DocumentToContentConverterImpl}): {@code
 * text/*}, {@code application/json}, {@code application/xml}, {@code application/yaml} → {@link
 * Modality#TEXT}; the four common image MIME types → {@link Modality#IMAGE}; {@code
 * application/pdf} → {@link Modality#DOCUMENT}. Audio + video MIME types map for completeness but
 * no emitter consumes them yet (Phase G+).
 */
public final class DocumentModality {

  private static final Set<String> TEXT_MIME_TYPES =
      Set.of("application/json", "application/xml", "application/yaml");

  private static final Set<String> IMAGE_MIME_TYPES =
      Set.of("image/jpeg", "image/png", "image/gif", "image/webp");

  private DocumentModality() {}

  /**
   * Resolves the modality of a document, throwing a {@link ConnectorException} when the MIME type
   * is missing or not part of the supported vocabulary. Mirrors the L4J converter's fail-loud
   * semantics on unsupported types.
   */
  public static Modality of(Document document) {
    final var mimeType = mimeTypeOf(document);
    return classify(mimeType)
        .orElseThrow(
            () ->
                new ConnectorException(
                    ERROR_CODE_FAILED_MODEL_CALL,
                    "Unsupported content type '%s' for document with reference '%s'"
                        .formatted(mimeType, document.reference())));
  }

  /**
   * Pure MIME → modality mapping. Returns empty for unsupported types so the caller can decide
   * whether to throw, fall back, or skip.
   */
  public static Optional<Modality> classify(String mimeType) {
    if (StringUtils.isBlank(mimeType)) {
      return Optional.empty();
    }
    final var normalised = mimeType.toLowerCase(Locale.ROOT).trim();
    final var bareType = stripParameters(normalised);

    if (bareType.startsWith("text/") || TEXT_MIME_TYPES.contains(bareType)) {
      return Optional.of(Modality.TEXT);
    }
    if (IMAGE_MIME_TYPES.contains(bareType)) {
      return Optional.of(Modality.IMAGE);
    }
    if ("application/pdf".equals(bareType)) {
      return Optional.of(Modality.DOCUMENT);
    }
    if (bareType.startsWith("audio/")) {
      return Optional.of(Modality.AUDIO);
    }
    if (bareType.startsWith("video/")) {
      return Optional.of(Modality.VIDEO);
    }
    return Optional.empty();
  }

  private static String mimeTypeOf(Document document) {
    return Optional.ofNullable(document.metadata())
        .map(DocumentMetadata::getContentType)
        .orElse(null);
  }

  private static String stripParameters(String mimeType) {
    final var semicolon = mimeType.indexOf(';');
    return semicolon >= 0 ? mimeType.substring(0, semicolon).trim() : mimeType;
  }
}
