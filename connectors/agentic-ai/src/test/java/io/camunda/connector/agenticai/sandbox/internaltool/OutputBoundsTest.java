/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.internaltool;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class OutputBoundsTest {

  // --- isBinary ---

  @Test
  void isBinary_plainText_shouldReturnFalse() {
    assertThat(OutputBounds.isBinary("hello world".getBytes(StandardCharsets.UTF_8))).isFalse();
  }

  @Test
  void isBinary_nulByte_shouldReturnTrue() {
    byte[] bytes = new byte[] {'h', 'i', 0x00, 'l', 'o'};
    assertThat(OutputBounds.isBinary(bytes)).isTrue();
  }

  @Test
  void isBinary_invalidUtf8_shouldReturnTrue() {
    // 0xFF is not valid UTF-8
    byte[] bytes = new byte[] {(byte) 0xFF, (byte) 0xFE};
    assertThat(OutputBounds.isBinary(bytes)).isTrue();
  }

  @Test
  void isBinary_emptyBytes_shouldReturnFalse() {
    assertThat(OutputBounds.isBinary(new byte[0])).isFalse();
  }

  // --- truncate ---

  @Test
  void truncate_underCap_notTruncated_shouldReturnOriginal() {
    String text = "hello";
    assertThat(OutputBounds.truncate(text, 100, false)).isEqualTo(text);
  }

  @Test
  void truncate_overCap_shouldContainTruncationMarkerAndTail() {
    String text = "AAAAAAAAAA" + "BBBBBBBBBB"; // 20 chars; cap at 10
    String result = OutputBounds.truncate(text, 10, false);
    assertThat(result).contains("truncated");
    assertThat(result).contains("BBBBBBBBBB"); // tail
    assertThat(result).doesNotContain("AAAAAAAAAA"); // head cut off
  }

  @Test
  void truncate_alreadyTruncated_shouldAlwaysAddMarker() {
    String text = "partial";
    String result = OutputBounds.truncate(text, 1000, true); // under cap but flagged
    assertThat(result).contains("truncated");
  }

  @Test
  void truncate_nullInput_shouldReturnEmptyString() {
    assertThat(OutputBounds.truncate(null, 100, false)).isEqualTo("");
  }

  // --- binaryOutputMarker ---

  @Test
  void binaryOutputMarker_shouldContainBinaryAndNotShown() {
    String marker = OutputBounds.binaryOutputMarker(1234L);
    assertThat(marker).contains("binary");
    assertThat(marker).contains("not shown");
  }

  // --- binaryFileMarker ---

  @Test
  void binaryFileMarker_shouldContainBinaryAndExportDocument() {
    String marker = OutputBounds.binaryFileMarker(2048L, "application/pdf");
    assertThat(marker).contains("binary");
    assertThat(marker).contains("sandbox_export_document");
  }

  // --- oversizedFileMarker ---

  @Test
  void oversizedFileMarker_shouldContainSizeAndExportDocument() {
    String marker = OutputBounds.oversizedFileMarker(2_000_000L, "text/plain");
    assertThat(marker).contains("too large");
    assertThat(marker).contains("sandbox_export_document");
  }
}
