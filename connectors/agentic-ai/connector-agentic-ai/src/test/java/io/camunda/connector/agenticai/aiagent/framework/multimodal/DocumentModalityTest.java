/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.multimodal;

import static io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilities.Modality;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DocumentModalityTest {

  @ParameterizedTest
  @CsvSource({
    "image/png, IMAGE",
    "image/jpeg, IMAGE",
    "application/pdf, DOCUMENT",
    "text/plain, TEXT",
    "text/csv, TEXT",
    "application/json, TEXT",
    "application/xml, TEXT",
    "application/yaml, TEXT",
    "application/vnd.api+json, TEXT",
    "audio/mpeg, AUDIO",
    "video/mp4, VIDEO",
    "application/octet-stream, DOCUMENT",
    "application/vnd.ms-excel, DOCUMENT"
  })
  void mapsMimeToModality(String contentType, Modality expected) {
    assertThat(DocumentModality.fromContentType(contentType)).isEqualTo(expected);
  }

  @Test
  void stripsParametersAndIsCaseInsensitive() {
    assertThat(DocumentModality.fromContentType("Text/Plain; charset=UTF-8"))
        .isEqualTo(Modality.TEXT);
  }

  @Test
  void defaultsToDocumentForNullOrBlank() {
    assertThat(DocumentModality.fromContentType(null)).isEqualTo(Modality.DOCUMENT);
    assertThat(DocumentModality.fromContentType("  ")).isEqualTo(Modality.DOCUMENT);
  }
}
