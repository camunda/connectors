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

import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.CHAT_COMPLETIONS_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.connector.e2e.ElementTemplate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Shared foundation for native-OpenAI-only v2 sub-process e2e coverage, scoped to the Chat
 * Completions API family - the sibling of {@link BaseOpenAiResponsesSubProcessTest} for Responses,
 * mirroring {@link BaseAnthropicSubProcessTest}.
 */
abstract class BaseOpenAiCompletionsSubProcessTest extends BaseAgentSubProcessV2Test {

  private static final String DEFAULT_MODEL = "test-model";

  @Override
  protected Function<ElementTemplate, ElementTemplate> providerConfigurer() {
    return this::configureOpenAiCompletionsBackend;
  }

  /**
   * Model id to configure alongside the backend wiring. Override to fix a different model for the
   * whole test class, or leave the default when a test doesn't care which model is used.
   */
  protected String defaultModel() {
    return DEFAULT_MODEL;
  }

  private ElementTemplate configureOpenAiCompletionsBackend(ElementTemplate template) {
    return template
        .property("provider.type", "openai")
        .property("provider.openai.api.type", "completions")
        .property("provider.openai.backend.type", "custom")
        .property("provider.openai.backend.custom.endpoint", wireMock.getHttpBaseUrl() + "/v1")
        .property("provider.openai.backend.custom.authentication.type", "apiKey")
        .property("provider.openai.backend.custom.authentication.apiKey", "dummy")
        .property("provider.openai.model.model", defaultModel());
  }

  static LoggedRequest soleRecordedRequest() {
    final var requests = recordedLoggedRequests();
    assertThat(requests).as("recorded model-call requests").hasSize(1);
    return requests.get(0);
  }

  static List<LoggedRequest> recordedLoggedRequests() {
    final List<LoggedRequest> requests =
        new ArrayList<>(findAll(postRequestedFor(urlPathEqualTo(CHAT_COMPLETIONS_PATH))));
    requests.sort(Comparator.comparing(LoggedRequest::getLoggedDate));
    return requests;
  }

  JsonNode parseBody(LoggedRequest loggedRequest) {
    try {
      return objectMapper.readTree(loggedRequest.getBodyAsString());
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to parse recorded OpenAI Chat Completions request body: "
              + loggedRequest.getBodyAsString(),
          e);
    }
  }
}
