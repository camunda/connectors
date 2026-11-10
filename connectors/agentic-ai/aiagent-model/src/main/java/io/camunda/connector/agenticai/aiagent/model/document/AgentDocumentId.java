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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

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
   * @throws UnsupportedDocumentReferenceException if the document is not a Camunda document
   */
  public static AgentDocumentId of(Document document) {
    return of(document.reference());
  }

  /**
   * The stable id of the document the reference points to: {@code "doc_"} followed by the first 12
   * lowercase hex characters of the SHA-256 digest of {@code "camunda"}, the store id and the
   * document id of the reference; the content hash and the metadata are not part of it. Neither the
   * store id nor the document id of the reference is exposed to the model by the id, and the same
   * document always gets the same id, so correlation and de-duplication work across the sites that
   * register and render a document.
   *
   * <p>The parts are hashed as the concatenation of their parts, each part written as a 4-byte
   * big-endian length followed by its UTF-8 bytes. An absent part is written as the length {@code
   * -1} and no bytes, so it differs from every present part.
   *
   * @throws UnsupportedDocumentReferenceException if the reference is not a Camunda document
   *     reference
   */
  public static AgentDocumentId of(DocumentReference reference) {
    if (!(reference instanceof CamundaDocumentReference camundaReference)) {
      throw new UnsupportedDocumentReferenceException(
          "Only Camunda documents are supported, but the document reference is not a Camunda document reference.");
    }
    final var digest = sha256();
    for (final var part :
        new String[] {"camunda", camundaReference.getStoreId(), camundaReference.getDocumentId()}) {
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
