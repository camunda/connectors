/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.model.document;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.api.document.DocumentReference.ExternalDocumentReference;
import io.camunda.connector.api.document.DocumentReference.InlineDocumentReference;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Derives a stable, model-safe id for a document. */
public final class DocumentHandle {

  private DocumentHandle() {}

  /**
   * Returns a stable, model-safe id for the given document:
   *
   * <ul>
   *   <li>{@link CamundaDocumentReference} → the documentId (already a UUID)
   *   <li>{@link ExternalDocumentReference} → {@code "ext-"} + first 12 hex chars of SHA-256(url);
   *       the raw URL is never exposed to the model
   *   <li>{@link InlineDocumentReference} with non-blank content → {@code "inline-"} + first 12 hex
   *       chars of SHA-256(content UTF-8); same content always produces the same id across
   *       population and render sites, so correlation and dedup work correctly
   *   <li>generic/null reference, or inline with blank/null content → random UUID (in-invocation
   *       only, not persisted); this is a residual edge case: the same logical document may receive
   *       different ids across calls, so dedup and correlation will not work for such documents.
   * </ul>
   */
  public static String idFor(Document document) {
    if (document == null) {
      return UUID.randomUUID().toString();
    }
    return idForReference(document.reference());
  }

  private static String idForReference(DocumentReference reference) {
    return switch (reference) {
      case CamundaDocumentReference ref -> ref.getDocumentId();
      case ExternalDocumentReference ref -> "ext-" + sha256Prefix(ref.url(), 12);
      case InlineDocumentReference ref when ref.content() != null && !ref.content().isBlank() ->
          "inline-" + sha256Prefix(ref.content(), 12);
      case null, default -> UUID.randomUUID().toString();
    };
  }

  static String sha256Prefix(String input, int hexChars) {
    try {
      final var digest = MessageDigest.getInstance("SHA-256");
      final var bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(bytes).substring(0, hexChars);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
