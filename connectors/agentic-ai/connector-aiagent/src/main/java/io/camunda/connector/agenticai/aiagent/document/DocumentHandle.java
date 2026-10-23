/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL;

import io.camunda.connector.agenticai.aiagent.model.document.DocumentId;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.api.document.DocumentReference.ExternalDocumentReference;
import io.camunda.connector.api.document.DocumentReference.InlineDocumentReference;
import io.camunda.connector.api.error.ConnectorException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

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
  public static DocumentId idFor(Document document) {
    return idForReference(document.reference(), document);
  }

  /**
   * Returns the file name the model is shown for the given document, or {@code null} when none is
   * known:
   *
   * <ul>
   *   <li>{@link CamundaDocumentReference} → the file name of the document metadata
   *   <li>{@link ExternalDocumentReference}, {@link InlineDocumentReference} → the reference {@code
   *       name}
   * </ul>
   *
   * Same result as {@link #fileNameFor(Map)} for the serialized reference of the document.
   */
  public static @Nullable String fileNameFor(Document document) {
    return switch (document.reference()) {
      case ExternalDocumentReference ref -> StringUtils.trimToNull(ref.name());
      case InlineDocumentReference ref -> StringUtils.trimToNull(ref.name());
      case null, default -> {
        final DocumentMetadata metadata = document.metadata();
        yield metadata != null ? StringUtils.trimToNull(metadata.getFileName()) : null;
      }
    };
  }

  /**
   * Returns the content type the model is shown for the given document, or {@code null} when it is
   * not known without fetching the content: the content type of the Camunda document metadata or
   * the {@code contentType} of an inline reference. Metadata of external documents is only
   * available by downloading the document, so they have none.
   *
   * <p>Same result as {@link #contentTypeFor(Map)} for the serialized reference of the document.
   */
  public static @Nullable String contentTypeFor(Document document) {
    return switch (document.reference()) {
      case ExternalDocumentReference ignored -> null;
      case InlineDocumentReference ref -> StringUtils.trimToNull(ref.contentType());
      case null, default -> {
        final DocumentMetadata metadata = document.metadata();
        yield metadata != null ? StringUtils.trimToNull(metadata.getContentType()) : null;
      }
    };
  }

  /**
   * Returns the size in bytes the model is shown for the given document, or {@code null} when it is
   * not known without reading the content: the size of the Camunda document metadata. Other
   * references have none.
   *
   * <p>Same result as {@link #sizeFor(Map)} for the serialized reference of the document.
   */
  public static @Nullable Long sizeFor(Document document) {
    return switch (document.reference()) {
      case CamundaDocumentReference ignored -> {
        final DocumentMetadata metadata = document.metadata();
        yield metadata != null ? metadata.getSize() : null;
      }
      case null, default -> null;
    };
  }

  /**
   * The file name of a serialized document reference (see {@link #fileNameFor(Document)}): {@code
   * metadata.fileName} of a Camunda reference, {@code name} of an external or inline one.
   */
  public static @Nullable String fileNameFor(Map<String, Object> reference) {
    return switch (referenceType(reference)) {
      case "camunda" -> metadataValue(reference, "fileName");
      case "external", "inline" -> text(reference.get("name"));
      default -> null;
    };
  }

  /**
   * The content type of a serialized document reference (see {@link #contentTypeFor(Document)}):
   * {@code metadata.contentType} of a Camunda reference, {@code contentType} of an inline one, none
   * for an external one.
   */
  public static @Nullable String contentTypeFor(Map<String, Object> reference) {
    return switch (referenceType(reference)) {
      case "camunda" -> metadataValue(reference, "contentType");
      case "inline" -> text(reference.get("contentType"));
      default -> null;
    };
  }

  /**
   * The size in bytes of a serialized document reference (see {@link #sizeFor(Document)}): {@code
   * metadata.size} of a Camunda reference, none for any other.
   */
  public static @Nullable Long sizeFor(Map<String, Object> reference) {
    if (!"camunda".equals(referenceType(reference))
        || !(reference.get("metadata") instanceof Map<?, ?> metadata)) {
      return null;
    }
    return metadata.get("size") instanceof Number size ? size.longValue() : null;
  }

  private static String referenceType(Map<String, Object> reference) {
    return reference.get("camunda.document.type") instanceof String type ? type : "";
  }

  private static @Nullable String metadataValue(Map<String, Object> reference, String key) {
    return reference.get("metadata") instanceof Map<?, ?> metadata ? text(metadata.get(key)) : null;
  }

  private static @Nullable String text(@Nullable Object value) {
    return value instanceof String string ? StringUtils.trimToNull(string) : null;
  }

  private static DocumentId idForReference(DocumentReference reference, Document document) {
    return switch (reference) {
      case CamundaDocumentReference ref -> DocumentId.of(ref.getDocumentId());
      case ExternalDocumentReference ref -> DocumentId.of("ext-" + sha256Prefix(ref.url(), 12));
      case InlineDocumentReference ref when ref.content() != null && !ref.content().isBlank() ->
          DocumentId.of("inline-" + sha256Prefix(ref.content(), 12));
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
