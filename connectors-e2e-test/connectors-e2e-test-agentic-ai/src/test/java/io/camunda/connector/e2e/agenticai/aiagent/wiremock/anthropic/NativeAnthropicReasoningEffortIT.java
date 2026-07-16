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
import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.AI_AGENT_TASK_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.wiremock.anthropic.AnthropicMessagesChatModelStubs.MESSAGES_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.agenticai.aiagent.jobworker.BaseAiAgentJobWorkerTest;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.anthropic.NativeAnthropicMessagesSseChatModelStubs.RedactedThinkingTurnStub;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.anthropic.NativeAnthropicMessagesSseChatModelStubs.ThinkingTurnStub;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.ToolCallStub;
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
 * Native-Anthropic-only e2e coverage for the Anthropic reasoning ({@code thinking}) and {@code
 * effort} configuration surface (own-LLM-layer / v2, ADR-009 R1 Task 5): proves that the new
 * element-template properties (Task 2), their request-side mapping (Task 3), and the response-side
 * round-trip (Task 4) all work end to end through the REAL Anthropic SDK types - the vendor SDK's
 * {@code BetaMessageAccumulator} on the response side, and its {@code MessageCreateParams} builder
 * on the request side - not just at the unit level.
 *
 * <p>Follows the same native-Anthropic wiring as {@link
 * NativeAnthropicSkillsAndToolsWireFormatTest} / {@link
 * NativeAnthropicCodeExecutionServerToolE2eTest}: v2/own-LLM-layer element template, {@code
 * configuration.anthropic.*} properties, {@link NativeAnthropicMessagesSseChatModelStubs} for the
 * streamed SSE response.
 */
class NativeAnthropicReasoningEffortIT extends BaseAiAgentJobWorkerTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  /**
   * Matches the {@code claude-sonnet-4-6-plus} capability-matrix entry (thinking modes {@code
   * enabled}/{@code adaptive}/{@code disabled}; effort levels {@code low}/{@code medium}/{@code
   * high}/{@code xhigh}/{@code max}), so every thinking mode and every non-custom effort level
   * under test validates successfully against a matched model.
   */
  private static final String REASONING_CAPABLE_MODEL = "claude-sonnet-4-6";

  /**
   * Matches the {@code claude-opus-4-6-plus} capability-matrix entry, whose declared thinking modes
   * are {@code adaptive}/{@code disabled} only (no {@code enabled}) - a matched model that does NOT
   * support the thinking mode under test in {@link
   * #failsFastWhenThinkingModeIsUnsupportedByTheMatchedModel()}.
   */
  private static final String ADAPTIVE_ONLY_MODEL = "claude-opus-4-8";

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
   * Mirrors {@link NativeAnthropicSkillsAndToolsWireFormatTest#createProcessInstance}. Each
   * {@code @Test} method supplies its own {@code elementTemplateModifier} (model id,
   * thinking/effort properties) which is applied AFTER the shared native-Anthropic backend wiring
   * below.
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
   * Points the connector at this test's WireMock server via the native (v2) Anthropic direct
   * backend, same wiring as {@link NativeAnthropicMessagesWireFormatFixture}. Deliberately does NOT
   * set {@code configuration.anthropic.model.model} - every test sets its own model id (via its
   * {@code elementTemplateModifier}) since the model id under test drives capability-matrix
   * matching and must vary per scenario.
   */
  private ElementTemplate configureAnthropicBackend(ElementTemplate template) {
    return template
        .property("configuration.type", "anthropic")
        .property("configuration.anthropic.backend.type", "direct")
        .property("configuration.anthropic.backend.direct.endpoint", wireMock.getHttpBaseUrl())
        .property("configuration.anthropic.backend.apiKey", "dummy");
  }

  private Function<ElementTemplate, ElementTemplate> model(String modelId) {
    return template -> template.property("configuration.anthropic.model.model", modelId);
  }

  // ---------------------------------------------------------------------------
  // Thinking configuration on the wire
  // ---------------------------------------------------------------------------

  @Test
  void enabledThinkingWithBudgetTokensAppearsOnTheWire() throws Exception {
    final var userPrompt = "Write a haiku about the sea";

    NativeAnthropicMessagesSseChatModelStubs.stubConversation(TurnStub.text("A haiku.", 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    final Function<ElementTemplate, ElementTemplate> elementTemplateModifier =
        model(REASONING_CAPABLE_MODEL)
            .andThen(
                template ->
                    template
                        .property(
                            "configuration.anthropic.model.parameters.thinking.mode", "enabled")
                        .property(
                            "configuration.anthropic.model.parameters.thinking.budgetTokens",
                            "=2048"));

    awaitProcessCompletion(
        createProcessInstance(elementTemplateModifier, Map.of("userPrompt", userPrompt)));

    final var request = parseBody(soleRecordedRequest());
    final var thinking = request.path("thinking");
    assertThat(thinking.path("type").asText()).as("thinking.type").isEqualTo("enabled");
    assertThat(thinking.path("budget_tokens").asLong())
        .as("thinking.budget_tokens")
        .isEqualTo(2048L);
  }

  @Test
  void adaptiveThinkingWithSummarizedDisplayAppearsOnTheWire() throws Exception {
    final var userPrompt = "Write a haiku about the sea";

    NativeAnthropicMessagesSseChatModelStubs.stubConversation(TurnStub.text("A haiku.", 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    final Function<ElementTemplate, ElementTemplate> elementTemplateModifier =
        model(REASONING_CAPABLE_MODEL)
            .andThen(
                template ->
                    template
                        .property(
                            "configuration.anthropic.model.parameters.thinking.mode", "adaptive")
                        .property(
                            "configuration.anthropic.model.parameters.thinking.display",
                            "summarized"));

    awaitProcessCompletion(
        createProcessInstance(elementTemplateModifier, Map.of("userPrompt", userPrompt)));

    final var request = parseBody(soleRecordedRequest());
    final var thinking = request.path("thinking");
    assertThat(thinking.path("type").asText()).as("thinking.type").isEqualTo("adaptive");
    assertThat(thinking.path("display").asText()).as("thinking.display").isEqualTo("summarized");
  }

  @Test
  void disabledThinkingAppearsOnTheWire() throws Exception {
    final var userPrompt = "Write a haiku about the sea";

    NativeAnthropicMessagesSseChatModelStubs.stubConversation(TurnStub.text("A haiku.", 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    final Function<ElementTemplate, ElementTemplate> elementTemplateModifier =
        model(REASONING_CAPABLE_MODEL)
            .andThen(
                template ->
                    template.property(
                        "configuration.anthropic.model.parameters.thinking.mode", "disabled"));

    awaitProcessCompletion(
        createProcessInstance(elementTemplateModifier, Map.of("userPrompt", userPrompt)));

    final var request = parseBody(soleRecordedRequest());
    assertThat(request.path("thinking").path("type").asText())
        .as("thinking.type")
        .isEqualTo("disabled");
  }

  // ---------------------------------------------------------------------------
  // Effort configuration on the wire
  // ---------------------------------------------------------------------------

  @Test
  void xhighEffortAppearsOnTheWireAsOutputConfigEffort() throws Exception {
    final var userPrompt = "Write a haiku about the sea";

    NativeAnthropicMessagesSseChatModelStubs.stubConversation(TurnStub.text("A haiku.", 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    final Function<ElementTemplate, ElementTemplate> elementTemplateModifier =
        model(REASONING_CAPABLE_MODEL)
            .andThen(
                template ->
                    template.property("configuration.anthropic.model.parameters.effort", "xhigh"));

    awaitProcessCompletion(
        createProcessInstance(elementTemplateModifier, Map.of("userPrompt", userPrompt)));

    final var request = parseBody(soleRecordedRequest());
    assertThat(request.path("output_config").path("effort").asText())
        .as("output_config.effort")
        .isEqualTo("xhigh");
  }

  @Test
  void customEffortSendsFreeTextValueVerbatimOnTheWire() throws Exception {
    final var userPrompt = "Write a haiku about the sea";

    NativeAnthropicMessagesSseChatModelStubs.stubConversation(TurnStub.text("A haiku.", 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    final Function<ElementTemplate, ElementTemplate> elementTemplateModifier =
        model(REASONING_CAPABLE_MODEL)
            .andThen(
                template ->
                    template
                        .property("configuration.anthropic.model.parameters.effort", "custom")
                        .property(
                            "configuration.anthropic.model.parameters.customEffort", "ultra"));

    awaitProcessCompletion(
        createProcessInstance(elementTemplateModifier, Map.of("userPrompt", userPrompt)));

    final var request = parseBody(soleRecordedRequest());
    assertThat(request.path("output_config").path("effort").asText())
        .as("output_config.effort")
        .isEqualTo("ultra");
  }

  // ---------------------------------------------------------------------------
  // Round-trip replay: signed thinking block survives a follow-up model call
  // ---------------------------------------------------------------------------

  @Test
  void roundTripsSignedThinkingBlockBeforeToolResultOnFollowUpRequest() throws Exception {
    final var userPrompt = "Use the superflux tool on 5 and 3, thinking it through first.";
    final var thinkingText = "Let me reason through this superflux calculation step by step.";
    final var signature = "sig-e2e-abc123==";
    final var toolCallId = "toolu_01thinkE2E";
    final var satisfiedResponseText = "The superflux calculation of 5 and 3 is 24.";

    NativeAnthropicMessagesSseChatModelStubs.stubThinkingConversation(
        new ThinkingTurnStub(
            thinkingText,
            signature,
            List.of(new ToolCallStub(toolCallId, "SuperfluxProduct", "{\"a\": 5, \"b\": 3}")),
            10,
            20),
        TurnStub.text(satisfiedResponseText, 11, 22));
    enqueueUserFeedback(userSatisfiedFeedback());

    final Function<ElementTemplate, ElementTemplate> elementTemplateModifier =
        model(REASONING_CAPABLE_MODEL)
            .andThen(
                template ->
                    template
                        .property(
                            "configuration.anthropic.model.parameters.thinking.mode", "enabled")
                        .property(
                            "configuration.anthropic.model.parameters.thinking.budgetTokens",
                            "=2048"));

    awaitProcessCompletion(
        createProcessInstance(elementTemplateModifier, Map.of("userPrompt", userPrompt)));

    final var loggedRequests = recordedLoggedRequests();
    assertThat(loggedRequests).as("recorded model-call requests").hasSize(2);

    final var secondRequest = parseBody(loggedRequests.get(1));
    assertThinkingBlockRoundTripsBeforeToolResult(secondRequest, thinkingText, signature);
  }

  /**
   * Asserts the second request's {@code messages[]} carries the first turn's assistant message with
   * its {@code thinking} block replayed byte-identical (same {@code thinking}/{@code signature}
   * values) and positioned before the {@code tool_use} block, exactly as the domain content
   * ordering preserves it (content before toolCalls, see {@code
   * AnthropicMessageRequestConverter#assistantParam}) - proving the response-side {@code
   * ReasoningContent} capture and the request-side round-trip both work end to end through the real
   * accumulator, not just at the unit level. Also asserts the stub accepted the replayed request
   * without error (implicit: {@code awaitProcessCompletion} above already proved the process
   * reached completion, i.e. the second request was answered with 200, not a 4xx).
   */
  private void assertThinkingBlockRoundTripsBeforeToolResult(
      JsonNode request, String expectedThinking, String expectedSignature) {
    final var assistantMessage =
        StreamSupport.stream(request.path("messages").spliterator(), false)
            .filter(message -> "assistant".equals(message.path("role").asText()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No assistant message found in second request"));

    final var contentBlocks = assistantMessage.path("content");
    final var contentBlockTypes =
        StreamSupport.stream(contentBlocks.spliterator(), false)
            .map(block -> block.path("type").asText())
            .toList();
    assertThat(contentBlockTypes)
        .as("assistant history content block types, in order")
        .containsExactly("thinking", "tool_use");

    final var thinkingBlock = contentBlocks.get(0);
    assertThat(thinkingBlock.path("thinking").asText())
        .as("round-tripped thinking text")
        .isEqualTo(expectedThinking);
    assertThat(thinkingBlock.path("signature").asText())
        .as("round-tripped thinking signature")
        .isEqualTo(expectedSignature);
  }

  // ---------------------------------------------------------------------------
  // Round-trip replay: redacted thinking block survives a follow-up model call
  // ---------------------------------------------------------------------------

  @Test
  void roundTripsRedactedThinkingBlockBeforeToolResultOnFollowUpRequest() throws Exception {
    final var userPrompt = "Use the superflux tool on 5 and 3, thinking it through first.";
    final var redactedData = "redacted-e2e-data-xyz==";
    final var toolCallId = "toolu_01redactedE2E";
    final var satisfiedResponseText = "The superflux calculation of 5 and 3 is 24.";

    NativeAnthropicMessagesSseChatModelStubs.stubRedactedThinkingConversation(
        new RedactedThinkingTurnStub(
            redactedData,
            List.of(new ToolCallStub(toolCallId, "SuperfluxProduct", "{\"a\": 5, \"b\": 3}")),
            10,
            20),
        TurnStub.text(satisfiedResponseText, 11, 22));
    enqueueUserFeedback(userSatisfiedFeedback());

    final Function<ElementTemplate, ElementTemplate> elementTemplateModifier =
        model(REASONING_CAPABLE_MODEL)
            .andThen(
                template ->
                    template
                        .property(
                            "configuration.anthropic.model.parameters.thinking.mode", "enabled")
                        .property(
                            "configuration.anthropic.model.parameters.thinking.budgetTokens",
                            "=2048"));

    awaitProcessCompletion(
        createProcessInstance(elementTemplateModifier, Map.of("userPrompt", userPrompt)));

    final var loggedRequests = recordedLoggedRequests();
    assertThat(loggedRequests).as("recorded model-call requests").hasSize(2);

    final var secondRequest = parseBody(loggedRequests.get(1));
    assertRedactedThinkingBlockRoundTripsBeforeToolResult(secondRequest, redactedData);
  }

  /**
   * Asserts the second request's {@code messages[]} carries the first turn's assistant message with
   * its {@code redacted_thinking} block replayed byte-identical (same {@code data} value) and
   * positioned before the {@code tool_use} block - mirrors {@link
   * #assertThinkingBlockRoundTripsBeforeToolResult(JsonNode, String, String)}, just for a redacted
   * block (no {@code thinking}/{@code signature} fields, only opaque {@code data}) instead of a
   * signed one.
   */
  private void assertRedactedThinkingBlockRoundTripsBeforeToolResult(
      JsonNode request, String expectedData) {
    final var assistantMessage =
        StreamSupport.stream(request.path("messages").spliterator(), false)
            .filter(message -> "assistant".equals(message.path("role").asText()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No assistant message found in second request"));

    final var contentBlocks = assistantMessage.path("content");
    final var contentBlockTypes =
        StreamSupport.stream(contentBlocks.spliterator(), false)
            .map(block -> block.path("type").asText())
            .toList();
    assertThat(contentBlockTypes)
        .as("assistant history content block types, in order")
        .containsExactly("redacted_thinking", "tool_use");

    final var redactedThinkingBlock = contentBlocks.get(0);
    assertThat(redactedThinkingBlock.path("data").asText())
        .as("round-tripped redacted thinking data")
        .isEqualTo(expectedData);
  }

  // ---------------------------------------------------------------------------
  // Validation failure: fails fast, before any HTTP call
  // ---------------------------------------------------------------------------

  @Test
  void failsFastWhenThinkingModeIsUnsupportedByTheMatchedModel() throws Exception {
    // claude-opus-4-8 matches the claude-opus-4-6-plus capability-matrix entry, whose declared
    // thinking modes are adaptive/disabled only - ENABLED is not among them, so the reasoning
    // validator (Task 3, spec §6 rule 2) must fail fast before any HTTP call is issued.
    final Function<ElementTemplate, ElementTemplate> elementTemplateModifier =
        model(ADAPTIVE_ONLY_MODEL)
            .andThen(
                template ->
                    template
                        .property(
                            "configuration.anthropic.model.parameters.thinking.mode", "enabled")
                        .property(
                            "configuration.anthropic.model.parameters.thinking.budgetTokens",
                            "=2048"));

    final var zeebeTest =
        awaitActiveIncidents(
            createProcessInstance(
                elementTemplateModifier, Map.of("userPrompt", "Write a haiku about the sea")));

    assertIncident(
        zeebeTest,
        incident -> {
          assertThat(incident.getElementId()).isEqualTo(AI_AGENT_TASK_ID);
          assertThat(incident.getErrorMessage()).contains("FAILED_MODEL_CALL");
          assertThat(incident.getErrorMessage())
              .contains("does not support thinking mode")
              .contains(ADAPTIVE_ONLY_MODEL);
        });

    assertThat(recordedLoggedRequests())
        .as("no HTTP call must have been made before validation failed")
        .isEmpty();
    assertThat(userFeedbackJobWorkerCounter.get())
        .as("user feedback must not be reached on a validation failure")
        .isZero();
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
