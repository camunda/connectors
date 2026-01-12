/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.camunda.connector.agenticai.mcp.client.model.result.McpClientGetPromptResult;
import io.camunda.connector.api.document.DocumentFactory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class McpClientResultDocumentHandlerTest {

  @Mock private DocumentFactory documentFactory;

  private final McpClientResultDocumentHandler testee =
      new McpClientResultDocumentHandler(documentFactory);

  @Test
  void passesThrough_whenNoBinaryDocumentContainer() {
    final var givenResult =
        new McpClientGetPromptResult(
            "Code review",
            List.of(
                new McpClientGetPromptResult.TextMessage(
                    "USER", "Please review the following code.")));

    final var transformedResult = testee.transformBinariesToDocumentsIfPresent(givenResult);

    assertThat(givenResult).isEqualTo(transformedResult);
  }
}
