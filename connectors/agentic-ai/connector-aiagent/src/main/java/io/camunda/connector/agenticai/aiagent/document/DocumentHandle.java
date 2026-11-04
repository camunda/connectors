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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * Derives a stable id for a document and reads the descriptors of its reference.
 *
 * <p>The id is the caller-visible handle other code cross-references a document by (the document
 * workspace, the tag the model reads, the arguments of a tool call). It is never sanitized for a
 * specific provider's wire-format constraints. A provider whose native id field has narrower
 * requirements (e.g. Bedrock's {@code DocumentBlock.name}) derives its own wire-safe value from
 * this id at its own call site instead of relying on this class to produce one.
 */
public final class DocumentHandle {

  /** The prefix of every document id. */
  public static final String ID_PREFIX = "doc_";

  private static final int ID_HEX_CHARS = 12;
  private static final HexFormat HEX_FORMAT = HexFormat.of();

  private DocumentHandle() {}

  /**
   * Returns the stable id of the given document, see {@link #idFor(DocumentReference)}.
   *
   * @throws ConnectorException if the reference type is not supported
   */
  public static DocumentId idFor(Document document) {
    return idFor(document.reference());
  }

  /**
   * Returns the stable id of the document the reference points to: {@code "doc_"} followed by the
   * first 12 lowercase hex characters of the SHA-256 digest of the {@linkplain
   * #identityKey(DocumentReference) identity key}. Neither the store id, the document id nor the
   * URL of the reference is exposed to the model by the id, and the same document always gets the
   * same id, so correlation and de-duplication work across the sites that register and render a
   * document.
   *
   * <p>The identity key is hashed as the concatenation of its parts, each part written as a 4-byte
   * big-endian length followed by its UTF-8 bytes.
   *
   * @throws ConnectorException if the reference type is not supported
   */
  public static DocumentId idFor(DocumentReference reference) {
    final var digest = sha256();
    for (final var part : identityKey(reference)) {
      final var bytes = part.getBytes(StandardCharsets.UTF_8);
      digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
      digest.update(bytes);
    }
    return DocumentId.of(
        ID_PREFIX + HEX_FORMAT.formatHex(digest.digest()).substring(0, ID_HEX_CHARS));
  }

  /**
   * Returns the parts that identify the document the reference points to; two references with equal
   * identity keys point to the same document:
   *
   * <ul>
   *   <li>{@link CamundaDocumentReference}: {@code "camunda"}, the store id and the document id;
   *       the content hash and the metadata are not part of it
   *   <li>{@link ExternalDocumentReference}: {@code "external"} and the URL
   *   <li>{@link InlineDocumentReference} with non-blank content: {@code "inline"} and the content
   * </ul>
   *
   * <p>Any other reference type, or an {@link InlineDocumentReference} with blank content, throws
   * rather than falling back to a random id: a fallback would produce a valid-looking handle over
   * an unsupported payload instead of surfacing the real problem.
   *
   * <p>The {@code case null, default} arm is compiler-mandated even though only the three reference
   * types above are expected: {@link DocumentReference} is a plain, non-sealed interface, so the
   * compiler cannot prove the switch is exhaustive without it.
   *
   * @throws ConnectorException if the reference type is not supported
   */
  public static List<String> identityKey(DocumentReference reference) {
    return switch (reference) {
      case CamundaDocumentReference ref ->
          List.of("camunda", String.valueOf(ref.getStoreId()), String.valueOf(ref.getDocumentId()));
      case ExternalDocumentReference ref -> List.of("external", String.valueOf(ref.url()));
      case InlineDocumentReference ref when ref.content() != null && !ref.content().isBlank() ->
          List.of("inline", ref.content());
      case null, default ->
          throw new ConnectorException(
              ERROR_CODE_FAILED_MODEL_CALL,
              "Unsupported document reference type '%s' for document with reference '%s'"
                  .formatted(
                      reference == null ? "null" : reference.getClass().getSimpleName(),
                      reference));
    };
  }

  /**
   * Returns the file name the model is shown for the given document, or {@code null} when none is
   * known: the file name of the metadata of a Camunda document, the {@code name} of an external or
   * inline reference.
   */
  public static @Nullable String fileNameFor(Document document) {
    return switch (document.reference()) {
      case ExternalDocumentReference ref -> fileNameFor(ref);
      case InlineDocumentReference ref -> fileNameFor(ref);
      case null, default -> {
        final DocumentMetadata metadata = document.metadata();
        yield metadata != null ? StringUtils.trimToNull(metadata.getFileName()) : null;
      }
    };
  }

  /**
   * Returns the content type the model is shown for the given document, or {@code null} when it is
   * not known without fetching the content: the content type of the metadata of a Camunda document
   * or the {@code contentType} of an inline reference. Metadata of external documents is only
   * available by downloading the document, so they have none.
   */
  public static @Nullable String contentTypeFor(Document document) {
    return switch (document.reference()) {
      case ExternalDocumentReference ref -> contentTypeFor(ref);
      case InlineDocumentReference ref -> contentTypeFor(ref);
      case null, default -> {
        final DocumentMetadata metadata = document.metadata();
        yield metadata != null ? StringUtils.trimToNull(metadata.getContentType()) : null;
      }
    };
  }

  /**
   * Returns the size in bytes the model is shown for the given document, or {@code null} when it is
   * not known without reading the content: the size of the metadata of a Camunda document. Other
   * references have none.
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
   * Returns the file name the model is shown for the document the reference points to, or {@code
   * null} when none is known: the file name of the metadata of a Camunda reference, the {@code
   * name} of an external or inline reference.
   */
  public static @Nullable String fileNameFor(DocumentReference reference) {
    return switch (reference) {
      case CamundaDocumentReference ref -> {
        final DocumentMetadata metadata = ref.getMetadata();
        yield metadata != null ? StringUtils.trimToNull(metadata.getFileName()) : null;
      }
      case ExternalDocumentReference ref -> StringUtils.trimToNull(ref.name());
      case InlineDocumentReference ref -> StringUtils.trimToNull(ref.name());
      case null, default -> null;
    };
  }

  /**
   * Returns the content type the model is shown for the document the reference points to, or {@code
   * null} when it is not known without fetching the content: the content type of the metadata of a
   * Camunda reference or the {@code contentType} of an inline reference. An external reference has
   * none.
   */
  public static @Nullable String contentTypeFor(DocumentReference reference) {
    return switch (reference) {
      case CamundaDocumentReference ref -> {
        final DocumentMetadata metadata = ref.getMetadata();
        yield metadata != null ? StringUtils.trimToNull(metadata.getContentType()) : null;
      }
      case InlineDocumentReference ref -> StringUtils.trimToNull(ref.contentType());
      case null, default -> null;
    };
  }

  /**
   * Returns the size in bytes the model is shown for the document the reference points to, or
   * {@code null} when it is not known without reading the content: the size of the metadata of a
   * Camunda reference. Other references have none.
   */
  public static @Nullable Long sizeFor(DocumentReference reference) {
    if (reference instanceof CamundaDocumentReference ref) {
      final DocumentMetadata metadata = ref.getMetadata();
      return metadata != null ? metadata.getSize() : null;
    }
    return null;
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
