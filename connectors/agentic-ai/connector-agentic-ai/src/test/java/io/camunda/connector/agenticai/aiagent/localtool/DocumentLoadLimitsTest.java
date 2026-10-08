/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.localtool;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DocumentLoadLimitsTest {

  @Test
  void textLimitIs256KbAndBinaryLimitIs10Mb() {
    assertThat(DocumentLoadLimits.TEXT_LIMIT_BYTES).isEqualTo(256 * 1024);
    assertThat(DocumentLoadLimits.BINARY_LIMIT_BYTES).isEqualTo(10 * 1024 * 1024);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "text/plain",
        "text/csv",
        "text/html; charset=utf-8",
        "TEXT/Markdown",
        "application/json",
        "application/json; charset=utf-8",
        "application/xml",
        "application/yaml",
        "application/x-yaml",
        "application/csv",
        "application/ld+json",
        "application/problem+json",
        "application/xhtml+xml",
        "image/svg+xml",
        "application/x-ndjson"
      })
  void textBasedContentTypesGetTheTextLimit(String contentType) {
    assertThat(DocumentLoadLimits.limitFor(contentType))
        .isEqualTo(DocumentLoadLimits.TEXT_LIMIT_BYTES);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "application/pdf",
        "image/png",
        "image/jpeg",
        "application/octet-stream",
        "application/zip",
        "audio/mpeg"
      })
  void otherContentTypesGetTheBinaryLimit(String contentType) {
    assertThat(DocumentLoadLimits.limitFor(contentType))
        .isEqualTo(DocumentLoadLimits.BINARY_LIMIT_BYTES);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void anUnknownContentTypeGetsTheTextLimit(String contentType) {
    assertThat(DocumentLoadLimits.limitFor(contentType))
        .isEqualTo(DocumentLoadLimits.TEXT_LIMIT_BYTES);
  }
}
