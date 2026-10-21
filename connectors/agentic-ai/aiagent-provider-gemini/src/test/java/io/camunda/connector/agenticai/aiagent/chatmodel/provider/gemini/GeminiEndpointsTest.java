/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GeminiEndpointsTest {

  @Test
  void shouldResolveMultiRegionalDefaultHost() {
    assertThat(GeminiEndpoints.defaultGoogleGenAiBaseUrl("us"))
        .isEqualTo("https://aiplatform.us.rep.googleapis.com");
    assertThat(GeminiEndpoints.defaultGoogleGenAiBaseUrl("eu"))
        .isEqualTo("https://aiplatform.eu.rep.googleapis.com");
    assertThat(GeminiEndpoints.defaultGoogleGenAiBaseUrl("EU"))
        .isEqualTo("https://aiplatform.eu.rep.googleapis.com");
  }
}
