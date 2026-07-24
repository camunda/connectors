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
package io.camunda.connector.e2e.agenticai.aiagent.subprocess.anthropic;

import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.camunda.connector.e2e.agenticai.aiagent.AgentTestFixtures.AI_AGENT_JOB_WORKER_V2_ELEMENT_TEMPLATE_PATH;
import static io.camunda.connector.e2e.agenticai.aiagent.AgentTestFixtures.AI_AGENT_JOB_WORKER_V2_ELEMENT_TEMPLATE_PROPERTIES;
import static io.camunda.connector.e2e.agenticai.aiagent.wiremock.anthropic.AnthropicMessagesChatModelStubs.MESSAGES_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.agenticai.aiagent.subprocess.BaseAgentSubProcessTest;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;

/**
 * Shared plumbing for native-Anthropic-only e2e coverage driven through the v2 element template:
 * points the connector at this test's WireMock server via the native Anthropic {@code compatible}
 * backend (the only Anthropic backend with a configurable endpoint), and provides the recorded
 * request lookup helpers used to assert on the wire format.
 */
abstract class BaseAnthropicNativeSubProcessTest extends BaseAgentSubProcessTest {

  static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Override
  protected String elementTemplatePath() {
    return AI_AGENT_JOB_WORKER_V2_ELEMENT_TEMPLATE_PATH;
  }

  @Override
  protected Map<String, String> elementTemplateProperties() {
    return AI_AGENT_JOB_WORKER_V2_ELEMENT_TEMPLATE_PROPERTIES;
  }

  /**
   * Overridden directly (rather than the {@code withOpenAiCompatibleProvider} hook {@link
   * BaseAgentSubProcessTest#createProcessInstance} composes) so this test's native-Anthropic
   * provider configuration - not the openaiCompatible default - configures the element template.
   */
  @Override
  protected ZeebeTest createProcessInstance(
      Resource process,
      Function<ElementTemplate, ElementTemplate> elementTemplateModifier,
      Map<String, Object> variables)
      throws IOException {
    final Function<ElementTemplate, ElementTemplate> composed =
        ((Function<ElementTemplate, ElementTemplate>) this::configureAnthropicBackend)
            .andThen(elementTemplateModifier);
    final var updatedElementTemplate =
        elementTemplateWithModifications(elementTemplatePath(), composed);
    final var updatedElementTemplateFile =
        updatedElementTemplate.writeTo(new File(tempDir, "template.json"));
    final var updatedModel = modelWithModifications(process.getFile(), updatedElementTemplateFile);
    return createProcessInstance(customizeModel(updatedModel), variables);
  }

  /**
   * Model id to configure alongside the backend wiring, or {@code null} to leave {@code
   * provider.anthropic.model.model} unset (when each {@code @Test} supplies its own model via a
   * {@code model(...)} modifier). Override to fix a single model for the whole test class.
   */
  protected @Nullable String defaultModel() {
    return null;
  }

  private ElementTemplate configureAnthropicBackend(ElementTemplate template) {
    final var configured =
        template
            .property("provider.type", "anthropic")
            .property("provider.anthropic.backend.type", "compatible")
            .property("provider.anthropic.backend.endpoint", wireMock.getHttpBaseUrl())
            .property("provider.anthropic.backend.compatibleAuthentication.type", "apiKey")
            .property("provider.anthropic.backend.compatibleAuthentication.apiKey", "dummy");
    final var defaultModel = defaultModel();
    return defaultModel != null
        ? configured.property("provider.anthropic.model.model", defaultModel)
        : configured;
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

  static JsonNode parseBody(LoggedRequest loggedRequest) {
    try {
      return OBJECT_MAPPER.readTree(loggedRequest.getBodyAsString());
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to parse recorded Anthropic messages request body: "
              + loggedRequest.getBodyAsString(),
          e);
    }
  }
}
