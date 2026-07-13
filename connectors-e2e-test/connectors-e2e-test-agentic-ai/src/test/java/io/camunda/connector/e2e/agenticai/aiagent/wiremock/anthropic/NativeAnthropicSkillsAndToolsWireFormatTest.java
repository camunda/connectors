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
package io.camunda.connector.e2e.agenticai.aiagent.wiremock.anthropic;

import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.AI_AGENT_JOB_WORKER_V2_ELEMENT_TEMPLATE_PATH;
import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.AI_AGENT_JOB_WORKER_V2_ELEMENT_TEMPLATE_PROPERTIES;
import static io.camunda.connector.e2e.agenticai.aiagent.wiremock.anthropic.AnthropicMessagesChatModelStubs.MESSAGES_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.agenticai.aiagent.jobworker.BaseAiAgentJobWorkerTest;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.TurnStub;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;

/**
 * Native-Anthropic-only e2e wire-format coverage for Anthropic Agent Skills and the {@code
 * code_execution}/{@code web_search}/{@code web_fetch} built-in tool toggles (C7 extension).
 *
 * <p>These are Anthropic-specific request shapes ({@code container.skills}, the auto-added {@code
 * code_execution} tool, the {@code anthropic-beta} header) that none of the other providers covered
 * by {@link io.camunda.connector.e2e.agenticai.aiagent.wiremock.ProviderWireFormatSmokeTests}
 * support, so this coverage lives in its own dedicated native-Anthropic test rather than as an
 * additional scenario in that cross-provider parameterized suite.
 *
 * <p>Server-side skill/tool execution can't be emulated by WireMock, so this test drives a single
 * plain-text turn (via {@link NativeAnthropicMessagesSseChatModelStubs}, already migrated to the
 * beta SSE event family) - the point is asserting the outgoing *request* wire format, not the
 * response.
 */
class NativeAnthropicSkillsAndToolsWireFormatTest extends BaseAiAgentJobWorkerTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
   * BaseAiAgentJobWorkerTest#createProcessInstance} composes) so this test's native-Anthropic
   * provider configuration - not the openaiCompatible default - configures the element template.
   * Mirrors {@code ProviderWireFormatSmokeTests#createProcessInstance}.
   */
  @Override
  protected ZeebeTest createProcessInstance(
      Resource process,
      Function<ElementTemplate, ElementTemplate> elementTemplateModifier,
      Map<String, Object> variables)
      throws IOException {
    final Function<ElementTemplate, ElementTemplate> composed =
        ((Function<ElementTemplate, ElementTemplate>) this::configureAnthropicSkillsAndTools)
            .andThen(elementTemplateModifier);
    final var updatedElementTemplate =
        elementTemplateWithModifications(elementTemplatePath(), composed);
    final var updatedElementTemplateFile =
        updatedElementTemplate.writeTo(new File(tempDir, "template.json"));
    final var updatedModel = modelWithModifications(process.getFile(), updatedElementTemplateFile);
    return createProcessInstance(customizeModel(updatedModel), variables);
  }

  /**
   * Points the connector at this test's WireMock server via the native (v2) Anthropic direct
   * backend (same wiring as {@link NativeAnthropicMessagesWireFormatFixture}) and configures two
   * Skills - one Anthropic-hosted (single-token form, defaulting type/version), one custom (3-token
   * form) - plus the web-search and web-fetch toggles. {@code enableCodeExecution} is left unset so
   * the {@code code_execution} tool/beta under test are proven to come from the skills auto-add
   * path, not the explicit toggle.
   */
  private ElementTemplate configureAnthropicSkillsAndTools(ElementTemplate template) {
    return template
        .property("configuration.type", "anthropic")
        .property("configuration.anthropic.backend.type", "direct")
        .property("configuration.anthropic.backend.direct.endpoint", wireMock.getHttpBaseUrl())
        .property("configuration.anthropic.backend.apiKey", "dummy")
        .property("configuration.anthropic.model.model", "test-model")
        .property("configuration.anthropic.skills", "=[\"pptx\", \"custom:my-skill:my-version\"]")
        .property("configuration.anthropic.enableWebSearch", "true")
        .property("configuration.anthropic.enableWebFetch", "true");
  }

  @Test
  void includesSkillsCodeExecutionAndWebToolsOnTheWire() throws Exception {
    final var userPrompt = "Write a haiku about the sea";

    NativeAnthropicMessagesSseChatModelStubs.stubConversation(TurnStub.text("A haiku.", 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    awaitProcessCompletion(createProcessInstance(Map.of("userPrompt", userPrompt)));

    final var loggedRequests = recordedLoggedRequests();
    assertThat(loggedRequests).as("recorded model-call requests").hasSize(1);

    final var request = parseBody(loggedRequests.get(0));

    assertContainerSkills(request);
    assertBuiltInTools(request);
    assertAnthropicBetaHeader(loggedRequests.get(0));
  }

  /**
   * Asserts the top-level {@code container.skills} array carries both configured skills, parsed per
   * {@code AnthropicSkillReference}'s {@code type:skill:version} rule: the single-token {@code
   * "pptx"} defaults to type {@code anthropic} / version {@code latest}, and the 3-token {@code
   * "custom:my-skill:my-version"} is taken as-is.
   */
  private void assertContainerSkills(JsonNode request) {
    final var skills = request.path("container").path("skills");
    assertThat(skills.isArray()).as("container.skills is present and an array").isTrue();

    final var skillTuples =
        StreamSupport.stream(skills.spliterator(), false)
            .map(
                skill ->
                    List.of(
                        skill.path("type").asText(),
                        skill.path("skill_id").asText(),
                        skill.path("version").asText()))
            .toList();

    assertThat(skillTuples)
        .as("container.skills entries")
        .containsExactlyInAnyOrder(
            List.of("anthropic", "pptx", "latest"), List.of("custom", "my-skill", "my-version"));
  }

  /**
   * Asserts {@code tools[]} contains exactly one auto-added {@code code_execution} tool (proving
   * the skills-triggered auto-add path, since {@code enableCodeExecution} was never set), plus the
   * {@code web_search} and {@code web_fetch} tools from their toggles.
   */
  private void assertBuiltInTools(JsonNode request) {
    final var toolTypes =
        StreamSupport.stream(request.path("tools").spliterator(), false)
            .map(tool -> tool.path("type").asText())
            .toList();

    assertThat(toolTypes)
        .as("tools[].type")
        .filteredOn("code_execution_20250825"::equals)
        .as("exactly one auto-added code_execution tool")
        .hasSize(1);
    assertThat(toolTypes).as("tools[].type").contains("web_search_20260318", "web_fetch_20260318");
  }

  /**
   * Asserts the {@code anthropic-beta} request header carries all three beta identifiers the skills
   * container and the auto-added {@code code_execution} tool require. The Anthropic SDK sends one
   * {@code addBeta(...)} call per repeated {@code anthropic-beta} header line (not one comma-joined
   * value), so all of the header's values - not just the first - must be inspected; order across
   * the repeated headers is not guaranteed.
   */
  private void assertAnthropicBetaHeader(LoggedRequest loggedRequest) {
    assertThat(loggedRequest.containsHeader("anthropic-beta"))
        .as("anthropic-beta header present")
        .isTrue();
    final var betaValues = loggedRequest.header("anthropic-beta").values();
    assertThat(betaValues)
        .as("anthropic-beta header values")
        .containsExactlyInAnyOrder(
            "code-execution-2025-08-25", "skills-2025-10-02", "files-api-2025-04-14");
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
