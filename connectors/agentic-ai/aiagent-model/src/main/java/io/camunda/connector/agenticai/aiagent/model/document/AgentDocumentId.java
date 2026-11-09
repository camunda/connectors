/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.api.document.DocumentReference.ExternalDocumentReference;
import io.camunda.connector.api.document.DocumentReference.InlineDocumentReference;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The stable id of a document the agent knows. The id is the handle a document is addressed by in
 * the {@link AgentDocumentHandle} the model reads, in the documents of the agent context and in the
 * arguments of a tool call. It is derived from the identity of the document, never the id the
 * document has in its store.
 *
 * <p>Serializes as a bare JSON string.
 *
 * @param value the id, never blank
 */
public record AgentDocumentId(@JsonValue String value) {

  /** The prefix of every document id. */
  public static final String ID_PREFIX = "doc_";

  private static final int ID_HEX_CHARS = 12;
  private static final HexFormat HEX_FORMAT = HexFormat.of();

  public AgentDocumentId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Document id cannot be null or empty");
    }
  }

  @JsonCreator
  public static AgentDocumentId of(String value) {
    return new AgentDocumentId(value);
  }

  /**
   * The stable id of the given document, see {@link #of(DocumentReference)}.
   *
   * @throws UnsupportedDocumentReferenceException if the reference type is not supported
   */
  public static AgentDocumentId of(Document document) {
    return of(document.reference());
  }

  /**
   * The stable id of the document the reference points to: {@code "doc_"} followed by the first 12
   * lowercase hex characters of the SHA-256 digest of the {@linkplain
   * #identityKey(DocumentReference) identity key}. Neither the store id, the document id nor the
   * URL of the reference is exposed to the model by the id, and the same document always gets the
   * same id, so correlation and de-duplication work across the sites that register and render a
   * document.
   *
   * <p>The identity key is hashed as the concatenation of its parts, each part written as a 4-byte
   * big-endian length followed by its UTF-8 bytes. An absent part is written as the length {@code
   * -1} and no bytes, so it differs from every present part.
   *
   * @throws UnsupportedDocumentReferenceException if the reference type is not supported
   */
  public static AgentDocumentId of(DocumentReference reference) {
    final var digest = sha256();
    for (final var part : identityKey(reference)) {
      if (part == null) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(-1).array());
        continue;
      }
      final var bytes = part.getBytes(StandardCharsets.UTF_8);
      digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
      digest.update(bytes);
    }
    return new AgentDocumentId(
        ID_PREFIX + HEX_FORMAT.formatHex(digest.digest()).substring(0, ID_HEX_CHARS));
  }

  /**
   * The parts that identify the document the reference points to; two references with equal
   * identity keys point to the same document:
   *
   * <ul>
   *   <li>{@link CamundaDocumentReference}: {@code "camunda"}, the store id and the document id;
   *       the content hash and the metadata are not part of it
   *   <li>{@link ExternalDocumentReference}: {@code "external"} and the URL
   *   <li>{@link InlineDocumentReference} with non-blank content: {@code "inline"} and the content
   * </ul>
   *
   * <p>Any other reference type, or an {@link InlineDocumentReference} with blank content, is
   * rejected rather than given a random id: a fallback would produce a valid-looking id over an
   * unsupported payload instead of surfacing the real problem.
   *
   * <p>The {@code case null, default} arm is compiler-mandated even though only the three reference
   * types above are expected: {@link DocumentReference} is a plain, non-sealed interface, so the
   * compiler cannot prove the switch is exhaustive without it.
   */
  static List<@Nullable String> identityKey(DocumentReference reference) {
    return switch (reference) {
      case CamundaDocumentReference ref -> parts("camunda", ref.getStoreId(), ref.getDocumentId());
      case ExternalDocumentReference ref -> parts("external", ref.url());
      case InlineDocumentReference ref when ref.content() != null && !ref.content().isBlank() ->
          parts("inline", ref.content());
      case null, default ->
          throw new UnsupportedDocumentReferenceException(
              "Unsupported document reference type '%s' for document with reference '%s'"
                  .formatted(
                      reference == null ? "null" : reference.getClass().getSimpleName(),
                      reference));
    };
  }

  private static List<@Nullable String> parts(@Nullable String... parts) {
    return Collections.unmodifiableList(Arrays.asList(parts));
  }

  /** The id as the model reads it. */
  @Override
  public String toString() {
    return value;
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
