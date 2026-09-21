/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.e2e.agenticai.aiagent.subprocess.provider.gemini;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.gemini.StreamingGeminiChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.TurnStub;
import io.camunda.connector.e2e.agenticai.assertj.AgentSubProcessResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Regression net for the {@code google-gemini-api} backend's credential-vs-inline resolution:
 * drives the connector through a saved {@code GoogleGeminiApiCredential} rather than the inline
 * {@code apiKey} field {@link BaseGeminiNativeSubProcessTest} otherwise configures — the wire
 * behavior itself is already exhaustively covered by {@link AgentSubProcessGeminiToolCallingTests}
 * and friends, so this only proves the credential path reaches the same wire call, not the full
 * tool-calling round trip again.
 */
@SlowTest
class AgentSubProcessGeminiApiCredentialTest extends BaseGeminiNativeSubProcessTest {

  @Override
  protected Function<ElementTemplate, ElementTemplate> providerConfigurer() {
    return template ->
        template
            .property("provider.type", "google-gemini")
            .property("provider.googleGemini.backend.type", "google-gemini-api")
            .property(
                "provider.googleGemini.backend.googleGeminiApi.endpoint", wireMock.getHttpBaseUrl())
            .property(
                "provider.googleGemini.backend.googleGeminiApi.googleGeminiApiCredential",
                "={\"apiKey\": \"dummy\"}")
            .property("provider.googleGemini.model.model", defaultModel());
  }

  @Test
  void resolvesApiKeyFromSavedCredential() throws Exception {
    final var userPrompt = "Write a haiku about the sea";
    final var responseText =
        "Salt spray on the wind / endless waves against the shore / silence after storm";

    StreamingGeminiChatModelStubs.stubConversation(TurnStub.text(responseText, 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(createProcessInstance(Map.of("userPrompt", userPrompt)));

    assertThat(soleRecordedRequest()).as("Gemini received exactly one request").isNotNull();
    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasResponseText(responseText));
  }
}
