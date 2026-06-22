/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.internaltool;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Shared helpers for output truncation and binary detection used by the tool handlers. Design §7a:
 * binary content is never returned raw — a marker string is substituted.
 */
final class OutputBounds {

  private OutputBounds() {}

  /**
   * Returns {@code true} if {@code bytes} look like binary data: contains a NUL byte or is not
   * valid UTF-8.
   */
  static boolean isBinary(byte[] bytes) {
    for (byte b : bytes) {
      if (b == 0) {
        return true;
      }
    }
    CharsetDecoder decoder =
        StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
    try {
      decoder.decode(ByteBuffer.wrap(bytes));
      return false;
    } catch (CharacterCodingException e) {
      return true;
    }
  }

  /**
   * Truncates {@code text} to at most {@code maxBytes} UTF-8 bytes, appending a truncation marker
   * line when truncation occurs. Uses last-N-bytes semantics (keeps the tail) so the most recent
   * output is always visible.
   *
   * @param text the text to truncate
   * @param maxBytes maximum number of UTF-8 bytes to keep
   * @param truncated pre-truncated flag from the SPI (when true, the marker is always appended)
   * @return the (possibly truncated) string
   */
  static String truncate(String text, long maxBytes, boolean truncated) {
    if (text == null || text.isEmpty()) {
      return text == null ? "" : text;
    }
    byte[] encoded = text.getBytes(StandardCharsets.UTF_8);
    if (!truncated && encoded.length <= maxBytes) {
      return text;
    }
    // Keep the last maxBytes bytes of the output (tail strategy — most recent output visible).
    int keepBytes = (int) Math.min(maxBytes, encoded.length);
    int startByte = encoded.length - keepBytes;
    // Find a valid UTF-8 start boundary.
    while (startByte < encoded.length && isContinuationByte(encoded[startByte])) {
      startByte++;
    }
    String tail =
        new String(encoded, startByte, encoded.length - startByte, StandardCharsets.UTF_8);
    int totalBytes = encoded.length;
    return "\n⟨output truncated — showing last "
        + keepBytes
        + " of "
        + totalBytes
        + " bytes⟩\n"
        + tail;
  }

  /** Binary output marker (substituted when stdout/stderr bytes look like binary). */
  static String binaryOutputMarker(long size) {
    return "⟨binary output, " + humanSize(size) + " — not shown⟩";
  }

  /**
   * Binary file read marker (substituted by {@code fs_read} when a file is binary or over cap).
   * Points the LLM toward {@code export_document}.
   */
  static String binaryFileMarker(long size, String contentType) {
    return "⟨binary, " + humanSize(size) + ", " + contentType + " — use export_document⟩";
  }

  /**
   * Oversized text file marker (substituted by {@code fs_read} when a text file exceeds the read
   * cap).
   */
  static String oversizedFileMarker(long size, String contentType) {
    return "⟨file too large to read ("
        + humanSize(size)
        + ", "
        + contentType
        + ") — use export_document or read a subsection via bash⟩";
  }

  private static boolean isContinuationByte(byte b) {
    // UTF-8 continuation bytes have the pattern 10xxxxxx (0x80–0xBF).
    return (b & 0xC0) == 0x80;
  }

  private static String humanSize(long bytes) {
    if (bytes < 1024) return bytes + " B";
    if (bytes < 1_048_576) return (bytes / 1024) + " KB";
    return (bytes / 1_048_576) + " MB";
  }
}
