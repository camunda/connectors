/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.request.structured;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.idp.extraction.request.common.extraction.DocumentAiExtractorRequest;
import io.camunda.connector.idp.extraction.request.common.extraction.TextractExtractorRequest;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Structured extraction only supports Google Document AI and AWS Textract. Every other extractor
 * must be rejected when the input is bound, not at execution.
 */
class StructuredExtractionRequestBindingTest {

  private static final String INPUT =
      """
      "input": {
        "document": {
          "camunda.document.type": "camunda",
          "storeId": "test",
          "documentId": "test",
          "metadata": {}
        },
        "includedFields": ["Invoice Number"],
        "renameMappings": {},
        "delimiter": ","
      }
      """;

  @Test
  void bindsTextract() {
    var request =
        bind(
            """
            "extractor": {
              "type": "textract",
              "awsAuthType": "credentials",
              "accessKey": "test-key",
              "secretKey": "test-secret",
              "region": "us-east-1",
              "bucketName": "test-bucket"
            },
            """);

    assertThat(request.extractor()).isInstanceOf(TextractExtractorRequest.class);
  }

  @Test
  void bindsDocumentAi() {
    var request =
        bind(
            """
            "extractor": {
              "type": "documentAi",
              "authType": "bearer",
              "bearerToken": "test-token",
              "projectId": "test-project",
              "region": "eu",
              "processorId": "test-processor"
            },
            """);

    assertThat(request.extractor()).isInstanceOf(DocumentAiExtractorRequest.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"pdfBox", "multimodal", "documentIntelligence", "abbyy", "unknown"})
  void rejectsExtractorsStructuredExtractionDoesNotSupport(String type) {
    assertThatThrownBy(() -> bind("\"extractor\": {\"type\": \"" + type + "\"},"))
        .hasMessageContaining(type);
  }

  @Test
  void rejectsExtractorWithoutType() {
    assertThatThrownBy(() -> bind("\"extractor\": {\"region\": \"us-east-1\"},"))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void rejectsMissingExtractor() {
    assertThatThrownBy(() -> bind("")).hasMessageContaining("extractor");
  }

  private static StructuredExtractionRequest bind(String extractor) {
    var context =
        OutboundConnectorContextBuilder.create().variables("{" + extractor + INPUT + "}").build();
    return context.bindVariables(StructuredExtractionRequest.class);
  }
}
