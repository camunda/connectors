/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.multimodal;

import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilities.Modality;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentMetadata;
import java.util.List;
import java.util.Locale;
import org.apache.hc.core5.http.ContentType;
import org.jspecify.annotations.Nullable;

/**
 * Maps a content's MIME type to the {@link Modality} bucket a provider ingests it as. Unknown/blank
 * (and unparseable) types map conservatively to {@link Modality#DOCUMENT} (which gates to the
 * synthetic fallback).
 *
 * <p>Mirrors the matching style of {@code DocumentToContentConverterImpl}: exact-match buckets are
 * expressed as constant {@link ContentType} lists matched via {@link #isCompatibleWithAnyOf}.
 * Unlike that converter (which only needs to recognise its own narrow supported subset), this class
 * must classify the full modality space, so the broad {@code image/}, {@code audio/}, {@code
 * video/} and {@code text/} families (plus the {@code +json}/{@code +xml} suffix conventions) stay
 * as open-ended prefix/suffix checks rather than enumerated lists.
 */
public final class DocumentModality {

  private static final List<ContentType> PDF_CONTENT_TYPES = List.of(ContentType.APPLICATION_PDF);

  private static final List<ContentType> ADDITIONAL_TEXT_FILE_CONTENT_TYPES =
      List.of(
          ContentType.APPLICATION_JSON,
          ContentType.APPLICATION_XML,
          ContentType.create("application/yaml"));

  private DocumentModality() {}

  /**
   * Resolves the {@link Modality} of a document's content type.
   *
   * <p><b>Note:</b> {@link Document#metadata()} and {@link DocumentMetadata#getContentType()} on an
   * {@code ExternalDocument} trigger a full download to resolve the content type (cached per
   * instance thereafter). Callers must not invoke this on documents they don't already render this
   * turn — a consideration future native-by-reference providers must account for.
   */
  public static Modality fromDocument(Document document) {
    final DocumentMetadata metadata = document.metadata();
    return fromContentType(metadata != null ? metadata.getContentType() : null);
  }

  public static Modality fromContentType(@Nullable String contentType) {
    if (contentType == null || contentType.isBlank()) {
      return Modality.DOCUMENT;
    }

    final ContentType parsed;
    try {
      parsed = ContentType.parse(contentType.trim().toLowerCase(Locale.ROOT));
    } catch (RuntimeException e) {
      return Modality.DOCUMENT;
    }
    if (parsed == null) {
      return Modality.DOCUMENT;
    }

    final var mime = parsed.getMimeType();
    if (mime.startsWith("image/")) {
      return Modality.IMAGE;
    }
    if (mime.startsWith("audio/")) {
      return Modality.AUDIO;
    }
    if (mime.startsWith("video/")) {
      return Modality.VIDEO;
    }
    if (isCompatibleWithAnyOf(parsed, PDF_CONTENT_TYPES)) {
      return Modality.DOCUMENT;
    }
    if (mime.startsWith("text/")
        || isCompatibleWithAnyOf(parsed, ADDITIONAL_TEXT_FILE_CONTENT_TYPES)
        || mime.equals("application/x-yaml")
        || mime.endsWith("+json")
        || mime.endsWith("+xml")) {
      return Modality.TEXT;
    }
    return Modality.DOCUMENT;
  }

  private static boolean isCompatibleWithAnyOf(
      ContentType contentType, List<ContentType> contentTypes) {
    return contentTypes.stream().anyMatch(contentType::isSameMimeType);
  }
}
