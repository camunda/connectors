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
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.anthropic.StreamingAnthropicMessagesSseChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.TurnStub;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;

/**
 * Native-Anthropic-only e2e coverage for the {@code provider.anthropic.enablePromptCaching}
 * element-template property (own-LLM-layer / v2): proves that enabling prompt caching actually adds
 * the top-level {@code cache_control: {"type": "ephemeral"}} field to the recorded wire request,
 * and that leaving it unset/off leaves the field off the wire.
 *
 * <p>Uses the v2/own-LLM-layer element template, {@code provider.anthropic.*} properties, and
 * {@link StreamingAnthropicMessagesSseChatModelStubs} for the streamed SSE response - mirrors
 * {@link AgentSubProcessAnthropicReasoningEffortTests}' wiring.
 */
class AgentSubProcessAnthropicPromptCachingTests extends BaseAgentSubProcessTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String MODEL = "claude-sonnet-4-6";

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
   * Mirrors {@link AgentSubProcessAnthropicReasoningEffortTests#createProcessInstance}.
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

  private ElementTemplate configureAnthropicBackend(ElementTemplate template) {
    return template
        .property("provider.type", "anthropic")
        .property("provider.anthropic.backend.type", "compatible")
        .property("provider.anthropic.backend.endpoint", wireMock.getHttpBaseUrl())
        .property("provider.anthropic.backend.compatibleAuthentication.type", "apiKey")
        .property("provider.anthropic.backend.compatibleAuthentication.apiKey", "dummy")
        .property("provider.anthropic.model.model", MODEL);
  }

  @Test
  void enablePromptCachingAddsCacheControlToTheWire() throws Exception {
    final var userPrompt = "Write a haiku about the sea";

    StreamingAnthropicMessagesSseChatModelStubs.stubConversation(TurnStub.text("A haiku.", 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    final Function<ElementTemplate, ElementTemplate> elementTemplateModifier =
        template -> template.property("provider.anthropic.enablePromptCaching", "true");

    awaitProcessCompletion(
        createProcessInstance(elementTemplateModifier, Map.of("userPrompt", userPrompt)));

    final var request = parseBody(soleRecordedRequest());
    assertThat(request.has("cache_control")).as("top-level cache_control present").isTrue();
    assertThat(request.path("cache_control").path("type").asText())
        .as("cache_control.type")
        .isEqualTo("ephemeral");
  }

  @Test
  void promptCachingDisabledByDefaultLeavesCacheControlOffTheWire() throws Exception {
    final var userPrompt = "Write a haiku about the sea";

    StreamingAnthropicMessagesSseChatModelStubs.stubConversation(TurnStub.text("A haiku.", 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    awaitProcessCompletion(createProcessInstance(Map.of("userPrompt", userPrompt)));

    final var request = parseBody(soleRecordedRequest());
    assertThat(request.has("cache_control"))
        .as("top-level cache_control must not be present when prompt caching is not enabled")
        .isFalse();
  }

  // ---------------------------------------------------------------------------
  // Shared plumbing
  // ---------------------------------------------------------------------------

  private static LoggedRequest soleRecordedRequest() {
    final var requests = recordedLoggedRequests();
    assertThat(requests).as("recorded model-call requests").hasSize(1);
    return requests.get(0);
  }

  private static List<LoggedRequest> recordedLoggedRequests() {
    final List<LoggedRequest> requests =
        new ArrayList<>(findAll(postRequestedFor(urlPathEqualTo(MESSAGES_PATH))));
    requests.sort(Comparator.comparing(LoggedRequest::getLoggedDate));
    return requests;
  }

  private static JsonNode parseBody(LoggedRequest loggedRequest) {
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
