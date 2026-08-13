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
package io.camunda.connector.e2e.agenticai.aiagent.task.v2;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.gemini.GeminiStreamGenerateContentRequests;
import java.util.List;
import java.util.function.Function;

/**
 * Base class for native-Gemini e2e tests on the AI Agent <b>Task</b> (v2 element template).
 *
 * <p>No native-provider {@code task/v2} precedent existed to copy: Anthropic's native provider only
 * has a {@code subprocess/v2} base class, and the only existing {@code task/v2} test uses the
 * {@code custom} provider backed by an in-process {@code ChatModelFactory} rather than real HTTP
 * traffic. This class therefore combines the two halves that did exist:
 *
 * <ul>
 *   <li>from {@code BaseAgentTaskV2Test}: the Task flavor's element template and property defaults,
 *       plus the {@code providerConfigurer()} composition hook that {@code BaseAgentTaskTest}
 *       applies to every process instance;
 *   <li>from {@code BaseAnthropicNativeSubProcessTest}: the native-provider wiring shape — the
 *       provider properties pointed at WireMock via the hidden {@code endpoint} field, an
 *       overridable {@code defaultModel()}, and the recorded-request helpers.
 * </ul>
 *
 * <p>It mirrors {@code BaseGeminiNativeSubProcessTest} apart from its superclass, carrying only the
 * helpers the Task-flavor tests actually use. Both bases delegate their recorded-request helpers to
 * {@link GeminiStreamGenerateContentRequests} rather than duplicating them, which is why that
 * helper lives in the wiremock package instead of on a base class the way Anthropic's single base
 * class could afford to.
 */
abstract class BaseGeminiNativeTaskV2Test extends BaseAgentTaskV2Test {

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
