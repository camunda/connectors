/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.textract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import io.camunda.connector.textract.model.TextractExecutionType;
import io.camunda.connector.textract.model.TextractRequest;
import org.junit.jupiter.api.Test;

/**
 * Reproduces the "Camunda Document" document-source defect.
 *
 * <p>{@code input.executionType} is annotated {@code @NotNull}, but its element-template property
 * is conditioned on {@code input.documentLocationType == "S3"}. Per the element-template contract,
 * "if a property is not active, it is not displayed, and its value is removed from the XML" — so a
 * model that selects the Camunda Document source sends no {@code executionType} at all, and binding
 * the request fails validation before the connector ever runs.
 *
 * <p>The variables below are exactly what such a model emits: {@code documentLocationType} set to
 * {@code UPLOADED}, and no {@code executionType} key. Every other conditional S3 property is
 * likewise absent.
 */
class TextractCamundaDocumentSourceTest {

  private static final String CAMUNDA_DOCUMENT_SOURCE_VARIABLES =
      """
      {
        "input": {
          "documentLocationType": "UPLOADED",
          "analyzeTables": true,
          "analyzeForms": true,
          "analyzeSignatures": true,
          "analyzeLayout": false,
          "analyzeQueries": false
        },
        "configuration": { "region": "eu-central-1" },
        "authentication": { "type": "defaultCredentialsChain" }
      }
      """;

  private static final String S3_SOURCE_VARIABLES_WITHOUT_EXECUTION_TYPE =
      """
      {
        "input": {
          "documentLocationType": "S3",
          "documentS3Bucket": "bucket",
          "documentName": "file.pdf",
          "analyzeTables": true,
          "analyzeForms": true,
          "analyzeSignatures": true,
          "analyzeLayout": false,
          "analyzeQueries": false
        },
        "configuration": { "region": "eu-central-1" },
        "authentication": { "type": "defaultCredentialsChain" }
      }
      """;

  @Test
  void bindsCamundaDocumentSourceWithoutExplicitExecutionType() {
    var context =
        OutboundConnectorContextBuilder.create()
            .variables(CAMUNDA_DOCUMENT_SOURCE_VARIABLES)
            .build();

    // FAILS before the fix: ConnectorInputException
    //   "Property: input.executionType: Validation failed. Original message: must not be null"
    assertThatCode(() -> context.bindVariables(TextractRequest.class)).doesNotThrowAnyException();

    // Only SYNC can analyze inline bytes — Textract's async/polling APIs require an S3 location.
    assertThat(context.bindVariables(TextractRequest.class).getInput().executionType())
        .isEqualTo(TextractExecutionType.SYNC);
  }

  @Test
  void stillRejectsMissingExecutionTypeForS3Source() {
    var context =
        OutboundConnectorContextBuilder.create()
            .variables(S3_SOURCE_VARIABLES_WITHOUT_EXECUTION_TYPE)
            .build();

    // The default is scoped to UPLOADED on purpose: the S3 source always renders executionType, so
    // a missing value there is a malformed model and must not silently degrade to single-page SYNC.
    assertThatThrownBy(() -> context.bindVariables(TextractRequest.class))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("input.executionType");
  }
}
