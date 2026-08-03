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

/**
 * Pins the "Camunda Document" document-source contract.
 *
 * <p>Until template v7 the {@code executionType} property was rendered only for the S3 source, and
 * an element template property whose condition is not met has its value removed from the BPMN XML.
 * A model selecting the Camunda document source therefore sent no {@code executionType} at all and
 * was rejected by the {@code @NotNull} on {@code TextractRequestData.executionType} before the
 * connector ever ran — the path was unusable.
 *
 * <p>From v7 the {@code uploadedExecutionType} property writes {@code SYNC} explicitly on that
 * branch, so the variable is always present. Models still on v6 or earlier must be upgraded to v7
 * in Modeler and redeployed; that is a deliberate choice, since the old path never worked and so
 * has no behavior worth preserving.
 */
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

    // SYNC is the only mode that can run here: Textract's StartDocumentAnalysis (polling and async)
    // takes a DocumentLocation, so it cannot analyze inline bytes.
    assertThat(context.bindVariables(TextractRequest.class).getInput().executionType())
        .isEqualTo(TextractExecutionType.SYNC);
  }

  @Test
  void rejectsPreV7ModelThatOmitsExecutionType() {
    var context =
        OutboundConnectorContextBuilder.create()
            .variables(PRE_V7_CAMUNDA_DOCUMENT_VARIABLES)
            .build();

    // Intentional: pre-v7 models must be upgraded to v7 in Modeler and redeployed. Defaulting the
    // missing value in code was considered and rejected — the old path always failed, so there is
    // no working behavior to stay compatible with.
    assertThatThrownBy(() -> context.bindVariables(TextractRequest.class))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("input.executionType");
  }
}
