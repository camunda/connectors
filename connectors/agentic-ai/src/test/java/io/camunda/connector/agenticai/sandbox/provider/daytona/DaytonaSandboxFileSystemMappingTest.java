/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.provider.daytona;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Pure unit tests for the file-extension mapping helpers in {@link DaytonaSandboxFileSystem}. No
 * SDK client or network access needed.
 */
class DaytonaSandboxFileSystemMappingTest {

  // ---------------------------------------------------------------------------
  // isBinaryByExtension
  // ---------------------------------------------------------------------------

  @ParameterizedTest(name = "{0} → not binary")
  @CsvSource({
    "/workspace/readme.txt",
    "/workspace/data.json",
    "/workspace/notes.md",
    "/workspace/data.csv",
    "/workspace/app.log",
    "/workspace/config.xml",
    "/workspace/config.yaml",
    "/workspace/config.yml",
    "/workspace/run.sh",
    "/workspace/script.py",
    "/workspace/app.js",
    "/workspace/app.ts",
    "/workspace/index.html",
    "/workspace/index.htm",
  })
  void isBinaryByExtension_textExtensions_returnFalse(String path) {
    assertThat(DaytonaSandboxFileSystem.isBinaryByExtension(path)).isFalse();
  }

  @ParameterizedTest(name = "{0} → binary")
  @CsvSource({
    "/workspace/document.pdf",
    "/workspace/image.png",
    "/workspace/photo.jpg",
    "/workspace/archive.zip",
    "/workspace/binary.bin",
    "/workspace/executable.exe",
    "/workspace/library.so",
  })
  void isBinaryByExtension_binaryExtensions_returnTrue(String path) {
    assertThat(DaytonaSandboxFileSystem.isBinaryByExtension(path)).isTrue();
  }

  @Test
  void isBinaryByExtension_noExtension_returnFalse() {
    assertThat(DaytonaSandboxFileSystem.isBinaryByExtension("/workspace/Makefile")).isFalse();
    assertThat(DaytonaSandboxFileSystem.isBinaryByExtension("/workspace/dockerfile")).isFalse();
  }

  // ---------------------------------------------------------------------------
  // detectContentType
  // ---------------------------------------------------------------------------

  @Test
  void detectContentType_json_returnsApplicationJson() {
    assertThat(DaytonaSandboxFileSystem.detectContentType("/workspace/data.json", false))
        .isEqualTo("application/json");
  }

  @Test
  void detectContentType_png_returnsImagePng() {
    assertThat(DaytonaSandboxFileSystem.detectContentType("/workspace/image.png", true))
        .isEqualTo("image/png");
  }

  @Test
  void detectContentType_unknownBinary_returnsOctetStream() {
    assertThat(DaytonaSandboxFileSystem.detectContentType("/workspace/file.bin", true))
        .isEqualTo("application/octet-stream");
  }

  @Test
  void detectContentType_unknownText_returnsTextPlain() {
    assertThat(DaytonaSandboxFileSystem.detectContentType("/workspace/file.xyz", false))
        .isEqualTo("text/plain");
  }

  @Test
  void detectContentType_noExtension_binaryTrue_returnsOctetStream() {
    assertThat(DaytonaSandboxFileSystem.detectContentType("/workspace/binary", true))
        .isEqualTo("application/octet-stream");
  }

  @Test
  void detectContentType_pdf_returnsApplicationPdf() {
    assertThat(DaytonaSandboxFileSystem.detectContentType("/workspace/doc.pdf", true))
        .isEqualTo("application/pdf");
  }

  @Test
  void detectContentType_yaml_returnsApplicationYaml() {
    assertThat(DaytonaSandboxFileSystem.detectContentType("/workspace/config.yaml", false))
        .isEqualTo("application/x-yaml");
  }
}
