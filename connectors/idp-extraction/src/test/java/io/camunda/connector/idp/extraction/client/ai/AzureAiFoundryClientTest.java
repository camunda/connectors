/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.client.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AzureAiFoundryClientTest {

  @ParameterizedTest
  @CsvSource({
    "https://idp.services.ai.azure.com/models, https://idp.services.ai.azure.com",
    "https://idp.services.ai.azure.com/models/, https://idp.services.ai.azure.com",
    "https://idp.services.ai.azure.com/, https://idp.services.ai.azure.com",
    "https://idp.services.ai.azure.com, https://idp.services.ai.azure.com",
    "https://models-idp.services.ai.azure.com/models, https://models-idp.services.ai.azure.com"
  })
  void shouldStripModelsPathFromEndpoint(String endpoint, String expected) {
    assertThat(AzureAiFoundryClient.toBaseEndpoint(endpoint)).isEqualTo(expected);
  }
}
