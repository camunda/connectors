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
package io.camunda.connector.e2e.agenticai.aiagent.subprocess.v2;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.gemini.GeminiStreamGenerateContentRequests;
import java.util.List;
import java.util.function.Function;

/**
 * Base class for native-Gemini e2e tests on the AI Agent Sub-process (v2 element template),
 * mirroring {@link BaseAnthropicNativeSubProcessTest}.
 *
 * <p>The hidden {@code endpoint} field is what makes this work: {@code GeminiChatModelFactory}
 * passes it to {@code HttpOptions.baseUrl()}, so the vendor SDK talks to WireMock instead of
 * Google. That field exists for exactly this reason and is never surfaced in the modeler.
 */
abstract class BaseGeminiNativeSubProcessTest extends BaseAgentSubProcessV2Test {

  /**
   * A Gemini 3.x model id, i.e. the {@code thinkingLevel} generation. Tests needing 2.5-style
   * {@code thinkingBudget} semantics override the id per test via {@link #model(String)}; the
   * WireMock stub matches any model id, so no stub change is needed.
   */
  private static final String DEFAULT_MODEL = "gemini-3-pro-preview";

  @Override
  protected Function<ElementTemplate, ElementTemplate> providerConfigurer() {
    return this::configureGeminiBackend;
  }

  /**
   * Model id to configure alongside the backend wiring. Override to fix a different model for the
   * whole test class, or leave the default when a test doesn't care which model is used.
   */
  protected String defaultModel() {
    return DEFAULT_MODEL;
  }

  private ElementTemplate configureGeminiBackend(ElementTemplate template) {
    return template
        .property("provider.type", "google-gemini")
        .property("provider.googleGemini.backend.type", "google-gemini-api")
        .property(
            "provider.googleGemini.backend.googleGeminiApi.endpoint", wireMock.getHttpBaseUrl())
        .property("provider.googleGemini.backend.googleGeminiApi.apiKey", "dummy")
        .property("provider.googleGemini.model.model", defaultModel());
  }

  static Function<ElementTemplate, ElementTemplate> model(String modelId) {
    return template -> template.property("provider.googleGemini.model.model", modelId);
  }

  static LoggedRequest soleRecordedRequest() {
    return GeminiStreamGenerateContentRequests.sole();
  }

  static List<LoggedRequest> recordedLoggedRequests() {
    return GeminiStreamGenerateContentRequests.recorded();
  }

  static List<LoggedRequest> recordedLoggedRequests(int expectedCount) {
    return GeminiStreamGenerateContentRequests.recorded(expectedCount);
  }

  JsonNode parseBody(LoggedRequest loggedRequest) {
    return GeminiStreamGenerateContentRequests.parseBody(loggedRequest);
  }

  static String requestedModel(LoggedRequest loggedRequest) {
    return GeminiStreamGenerateContentRequests.requestedModel(loggedRequest);
  }
}
