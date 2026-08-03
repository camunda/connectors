/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.textract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import io.camunda.connector.textract.model.TextractExecutionType;
import io.camunda.connector.textract.model.TextractRequest;
import org.junit.jupiter.api.Test;

/** Pins the "Camunda Document" document-source contract: v7 supplies sync, pre-v7 is rejected. */
class TextractCamundaDocumentSourceTest {

  /** What template v7 emits for the Camunda document source. */
  private static final String V7_CAMUNDA_DOCUMENT_VARIABLES =
      """
      {
        "input": {
          "documentLocationType": "UPLOADED",
          "executionType": "SYNC",
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

  /** What template v6 and earlier emitted: the conditional property is stripped entirely. */
  private static final String PRE_V7_CAMUNDA_DOCUMENT_VARIABLES =
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

  @Test
  void bindsCamundaDocumentSourceWhenTemplateSuppliesSync() {
    var context =
        OutboundConnectorContextBuilder.create().variables(V7_CAMUNDA_DOCUMENT_VARIABLES).build();

    // Sync is the only runnable mode here: StartDocumentAnalysis needs an S3 DocumentLocation.
    assertThat(context.bindVariables(TextractRequest.class).getInput().executionType())
        .isEqualTo(TextractExecutionType.SYNC);
  }

  @Test
  void rejectsPreV7ModelThatOmitsExecutionType() {
    var context =
        OutboundConnectorContextBuilder.create()
            .variables(PRE_V7_CAMUNDA_DOCUMENT_VARIABLES)
            .build();

    // Intentional: pre-v7 models must be upgraded and redeployed, not defaulted to sync in code.
    assertThatThrownBy(() -> context.bindVariables(TextractRequest.class))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("input.executionType");
  }
}
