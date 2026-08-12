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
import static io.camunda.connector.e2e.agenticai.aiagent.wiremock.anthropic.AnthropicMessagesChatModelStubs.MESSAGES_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.connector.e2e.ElementTemplate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

abstract class BaseAnthropicSubProcessTest extends BaseAgentSubProcessV2Test {

  private static final String DEFAULT_MODEL = "claude-sonnet-4-6";

  @Override
  protected Function<ElementTemplate, ElementTemplate> providerConfigurer() {
    return this::configureAnthropicBackend;
  }

  /**
   * Model id to configure alongside the backend wiring. Override to fix a different model for the
   * whole test class, or leave the default when a test doesn't care which model is used.
   */
  protected String defaultModel() {
    return DEFAULT_MODEL;
  }

  private ElementTemplate configureAnthropicBackend(ElementTemplate template) {
    return template
        .property("provider.type", "anthropic")
        .property("provider.anthropic.backend.type", "custom")
        .property("provider.anthropic.backend.custom.endpoint", wireMock.getHttpBaseUrl())
        .property("provider.anthropic.backend.custom.authentication.type", "apiKey")
        .property("provider.anthropic.backend.custom.authentication.apiKey", "dummy")
        .property("provider.anthropic.model.model", defaultModel());
  }

  static Function<ElementTemplate, ElementTemplate> model(String modelId) {
    return template -> template.property("provider.anthropic.model.model", modelId);
  }

  static LoggedRequest soleRecordedRequest() {
    final var requests = recordedLoggedRequests();
    assertThat(requests).as("recorded model-call requests").hasSize(1);
    return requests.get(0);
  }

  static List<LoggedRequest> recordedLoggedRequests() {
    final List<LoggedRequest> requests =
        new ArrayList<>(findAll(postRequestedFor(urlPathEqualTo(MESSAGES_PATH))));
    requests.sort(Comparator.comparing(LoggedRequest::getLoggedDate));
    return requests;
  }

  JsonNode parseBody(LoggedRequest loggedRequest) {
    try {
      return objectMapper.readTree(loggedRequest.getBodyAsString());
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to parse recorded Anthropic messages request body: "
              + loggedRequest.getBodyAsString(),
          e);
    }
  }
}
