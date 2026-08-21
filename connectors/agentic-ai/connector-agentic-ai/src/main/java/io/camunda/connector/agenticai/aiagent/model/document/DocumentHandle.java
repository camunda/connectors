/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.api.document.DocumentReference.ExternalDocumentReference;
import io.camunda.connector.api.document.DocumentReference.InlineDocumentReference;
import io.camunda.connector.api.error.ConnectorException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Derives a stable id for a document, verbatim wherever possible.
 *
 * <p>This id is the caller-visible handle other code cross-references a document by (e.g. a
 * document registry) - it is never sanitized or hashed away for a specific provider's wire-format
 * constraints. A provider whose native id field has narrower requirements (e.g. Bedrock's {@code
 * DocumentBlock.name}) derives its own wire-safe value from this id at its own call site instead of
 * relying on this class to produce one.
 */
public final class DocumentHandle {

  private static final HexFormat HEX_FORMAT = HexFormat.of();

  private DocumentHandle() {}

  /**
   * Returns a stable id for the given document:
   *
   * <ul>
   *   <li>{@link CamundaDocumentReference} → the documentId verbatim; usually a UUID, but {@code
   *       DocumentCreationRequest.documentId(...)} accepts arbitrary strings, so it isn't
   *       guaranteed to be one
   *   <li>{@link ExternalDocumentReference} → {@code "ext-"} + first 12 hex chars of SHA-256(url);
   *       the raw URL is never exposed to the model
   *   <li>{@link InlineDocumentReference} with non-blank content → {@code "inline-"} + first 12 hex
   *       chars of SHA-256(content UTF-8); same content always produces the same id across
   *       population and render sites, so correlation and dedup work correctly
   * </ul>
   *
   * <p>Any other reference type, or an {@link InlineDocumentReference} with blank content, throws
   * rather than falling back to a random id: a fallback would produce a valid-looking handle over
   * an unsupported payload instead of surfacing the real problem.
   *
   * <p>The {@code case null, default} arm is compiler-mandated even though only the three reference
   * types above are expected: {@link DocumentReference} is a plain, non-sealed interface, so the
   * compiler cannot prove the switch is exhaustive without it.
   */
  public static String idFor(Document document) {
    return idForReference(document.reference(), document);
  }

  private static String idForReference(DocumentReference reference, Document document) {
    return switch (reference) {
      case CamundaDocumentReference ref -> ref.getDocumentId();
      case ExternalDocumentReference ref -> "ext-" + sha256Prefix(ref.url(), 12);
      case InlineDocumentReference ref when ref.content() != null && !ref.content().isBlank() ->
          "inline-" + sha256Prefix(ref.content(), 12);
      case null, default ->
          throw new ConnectorException(
              ERROR_CODE_FAILED_MODEL_CALL,
              "Unsupported document reference type '%s' for document with reference '%s'"
                  .formatted(
                      reference == null ? "null" : reference.getClass().getSimpleName(),
                      document.reference()));
    };
  }

  private static String sha256Prefix(String input, int hexChars) {
    try {
      final var digest = MessageDigest.getInstance("SHA-256");
      final var bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return HEX_FORMAT.formatHex(bytes).substring(0, hexChars);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
