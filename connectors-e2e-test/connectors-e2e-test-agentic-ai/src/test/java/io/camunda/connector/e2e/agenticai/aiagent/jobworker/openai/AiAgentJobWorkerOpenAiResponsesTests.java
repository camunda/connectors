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
package io.camunda.connector.e2e.agenticai.aiagent.jobworker.openai;

import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.AI_AGENT_JOB_WORKER_V2_ELEMENT_TEMPLATE_PATH;
import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.AI_AGENT_JOB_WORKER_V2_ELEMENT_TEMPLATE_PROPERTIES;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.agenticai.aiagent.jobworker.BaseAiAgentJobWorkerTest;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.StreamingOpenAiResponsesRecordedConversation;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.StreamingOpenAiResponsesRecordedConversation.RecordedChatRequest;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.StreamingOpenAiResponsesSseChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.StreamingOpenAiResponsesSseChatModelStubs.ReasoningTurnStub;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.StreamingOpenAiResponsesSseChatModelStubs.ServerToolTurnStub;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.StreamingOpenAiResponsesWireFormatFixture;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.ToolCallStub;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.TurnStub;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;

/**
 * Native-OpenAI-Responses-only e2e coverage for the Responses-specific reasoning/encrypted-
 * reasoning/server-tool round-trips (own-LLM-layer / v2, ADR-009 vertical pilot, Task 4 of the
 * native OpenAI deterministic-tests plan): proves that {@code
 * provider.openai.model.parameters.effort} reaches the wire together with {@code include:
 * ["reasoning.encrypted_content"]}, that a reasoning item's opaque {@code encrypted_content}
 * round-trips byte-identical through the REAL vendor SDK {@code ResponseAccumulator}, and that a
 * server-tool item ({@code web_search_call}) is preserved as an opaque {@code ProviderContent}
 * block and replayed on a follow-up model call - the OpenAI analogue of {@code
 * AiAgentJobWorkerAnthropicReasoningEffortTests} and {@code
 * AiAgentJobWorkerAnthropicCodeExecutionTests}.
 *
 * <p>Reuses {@link StreamingOpenAiResponsesWireFormatFixture}'s native-OpenAI-Responses element
 * template wiring (v2/own-LLM-layer template, {@code provider.openai.*} properties, compatible
 * backend pointed at WireMock) and {@link StreamingOpenAiResponsesRecordedConversation} for request
 * inspection, extended (see {@link StreamingOpenAiResponsesRecordedConversation#rawInputItems()},
 * {@link StreamingOpenAiResponsesRecordedConversation.RecordedChatRequest#reasoningEffort()},
 * {@link StreamingOpenAiResponsesRecordedConversation.RecordedChatRequest#include()}) to expose the
 * item kinds and top-level fields the shared {@code messages()} SPI mapping doesn't model.
 */
class AiAgentJobWorkerOpenAiResponsesTests extends BaseAiAgentJobWorkerTest {

  /**
   * Matches the {@code openai-responses}/{@code gpt-5*} capability-matrix entry ({@code
   * provider.reasoning.effort-levels: [minimal, low, medium, high]}), so {@code effort=high} used
   * throughout this test validates successfully against a matched model - mirroring how {@code
   * AiAgentJobWorkerAnthropicReasoningEffortTests} picks {@code claude-sonnet-4-6}.
   */
  private static final String REASONING_CAPABLE_MODEL = "gpt-5";

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
   * BaseAiAgentJobWorkerTest#createProcessInstance} composes) so this test's
   * native-OpenAI-Responses provider configuration - not the openaiCompatible v1 default -
   * configures the element template. Mirrors {@code
   * AiAgentJobWorkerAnthropicReasoningEffortTests#createProcessInstance}.
   */
  @Override
  protected ZeebeTest createProcessInstance(
      Resource process,
      Function<ElementTemplate, ElementTemplate> elementTemplateModifier,
      Map<String, Object> variables)
      throws IOException {
    final Function<ElementTemplate, ElementTemplate> composed =
        ((Function<ElementTemplate, ElementTemplate>) this::configureOpenAiResponsesBackend)
            .andThen(elementTemplateModifier);
    final var updatedElementTemplate =
        elementTemplateWithModifications(elementTemplatePath(), composed);
    final var updatedElementTemplateFile =
        updatedElementTemplate.writeTo(new File(tempDir, "template.json"));
    final var updatedModel = modelWithModifications(process.getFile(), updatedElementTemplateFile);
    return createProcessInstance(customizeModel(updatedModel), variables);
  }

  /**
   * Points the connector at this test's WireMock server via the native (v2) OpenAI Responses
   * compatible backend, same wiring as {@link StreamingOpenAiResponsesWireFormatFixture}. Sets
   * {@code provider.openai.model.model} to {@link #REASONING_CAPABLE_MODEL} so effort validation
   * (Task 4's reasoning tests) and server-tool API-family validation both resolve against a
   * consistent, matched model across every test in this class.
   */
  private ElementTemplate configureOpenAiResponsesBackend(ElementTemplate template) {
    return template
        .property("provider.type", "openai")
        .property("provider.openai.apiFamily", "responses")
        .property("provider.openai.backend.type", "compatible")
        .property("provider.openai.backend.endpoint", wireMock.getHttpBaseUrl() + "/v1")
        .property("provider.openai.backend.authentication.type", "apiKey")
        .property("provider.openai.backend.authentication.apiKey", "dummy")
        .property("provider.openai.model.model", REASONING_CAPABLE_MODEL);
  }

  private Function<ElementTemplate, ElementTemplate> effort(String effort) {
    return template -> template.property("provider.openai.model.parameters.effort", effort);
  }

  // ---------------------------------------------------------------------------
  // Reasoning effort configuration on the wire
  // ---------------------------------------------------------------------------

  @Test
  void highEffortAppearsOnTheWireWithEncryptedContentInclude() throws Exception {
    final var userPrompt = "Write a haiku about the sea";

    StreamingOpenAiResponsesSseChatModelStubs.stubConversation(TurnStub.text("A haiku.", 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    awaitProcessCompletion(createProcessInstance(effort("high"), Map.of("userPrompt", userPrompt)));

    final var request = soleRecordedRequest();
    assertThat(request.reasoningEffort()).as("reasoning.effort").contains("high");
    assertThat(request.include())
        .as("include[] requests the encrypted reasoning payload")
        .contains("reasoning.encrypted_content");
  }

  // ---------------------------------------------------------------------------
  // Round-trip replay: encrypted reasoning item survives a follow-up model call
  // ---------------------------------------------------------------------------

  @Test
  void roundTripsEncryptedReasoningItemBeforeFunctionCallOnFollowUpRequest() throws Exception {
    final var userPrompt = "Use the superflux tool on 5 and 3, reasoning it through first.";
    final var reasoningId = "rs_e2e_1";
    final var encryptedContent = "ENC-OPAQUE-e2e-abc123==";
    final var toolCallId = "call_reasoningE2E";
    final var satisfiedResponseText = "The superflux calculation of 5 and 3 is 24.";

    StreamingOpenAiResponsesSseChatModelStubs.stubReasoningConversation(
        new ReasoningTurnStub(
            reasoningId,
            encryptedContent,
            List.of(new ToolCallStub(toolCallId, "SuperfluxProduct", "{\"a\": 5, \"b\": 3}")),
            10,
            20),
        TurnStub.text(satisfiedResponseText, 11, 22));
    enqueueUserFeedback(userSatisfiedFeedback());

    awaitProcessCompletion(createProcessInstance(effort("high"), Map.of("userPrompt", userPrompt)));

    final var recorded = StreamingOpenAiResponsesRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).as("recorded model-call requests").isEqualTo(2);

    final var secondRequest = recorded.requests().get(1);
    assertReasoningItemRoundTripsBeforeFunctionCall(
        secondRequest, reasoningId, encryptedContent, toolCallId);
  }

  /**
   * Asserts the second request's raw {@code input[]} array carries the first turn's reasoning item
   * replayed byte-identical (same {@code id}/{@code encrypted_content} values) and immediately
   * followed by the {@code function_call} item - exactly as {@code
   * OpenAiResponsesRequestConverter#assistantInputItems} orders a replayed assistant turn
   * (reasoning first, then any plain content, then function calls) - proving the response-side
   * {@code ReasoningContent} capture and the request-side round-trip both work end to end through
   * the real {@code ResponseAccumulator}, not just at the unit level (see {@code
   * OpenAiProviderContentRoundTripTest} for the equivalent unit-level proof).
   */
  private void assertReasoningItemRoundTripsBeforeFunctionCall(
      RecordedChatRequest request,
      String expectedReasoningId,
      String expectedEncryptedContent,
      String expectedToolCallId) {
    final List<JsonNode> items = request.rawInputItems();
    final List<String> itemTypes = items.stream().map(item -> item.path("type").asText()).toList();

    final int reasoningIndex = itemTypes.indexOf("reasoning");
    assertThat(reasoningIndex).as("reasoning item replayed in input[]").isNotEqualTo(-1);

    final JsonNode reasoningItem = items.get(reasoningIndex);
    assertThat(reasoningItem.path("id").asText())
        .as("round-tripped reasoning id")
        .isEqualTo(expectedReasoningId);
    assertThat(reasoningItem.path("encrypted_content").asText())
        .as("round-tripped reasoning encrypted_content")
        .isEqualTo(expectedEncryptedContent);

    assertThat(reasoningIndex + 1)
        .as("a function_call item immediately follows the reasoning item")
        .isLessThan(itemTypes.size());
    assertThat(itemTypes.get(reasoningIndex + 1))
        .as("item type immediately following the reasoning item")
        .isEqualTo("function_call");

    final JsonNode functionCallItem = items.get(reasoningIndex + 1);
    assertThat(functionCallItem.path("call_id").asText())
        .as("round-tripped function_call call_id")
        .isEqualTo(expectedToolCallId);
  }

  // ---------------------------------------------------------------------------
  // Round-trip replay: server-tool ProviderContent survives a follow-up model call
  // ---------------------------------------------------------------------------

  @Test
  void roundTripsWebSearchCallProviderContentIntoFollowUpModelCall() throws Exception {
    final var userPrompt = "Search the web to check today's date.";
    final var followUpPrompt = "Can you also check the weather?";
    final var webSearchText = "Let me check that for you.";
    final var webSearchCallId = "ws_e2e_1";
    final var searchQuery = "today's date";
    final var satisfiedResponseText = "Great, glad that helped!";

    StreamingOpenAiResponsesSseChatModelStubs.stubServerToolConversation(
        new ServerToolTurnStub(webSearchText, webSearchCallId, searchQuery, 10, 20),
        TurnStub.text(satisfiedResponseText, 11, 22));
    enqueueUserFeedback(userFollowUpFeedback(followUpPrompt), userSatisfiedFeedback());

    final Function<ElementTemplate, ElementTemplate> elementTemplateModifier =
        template -> template.property("provider.openai.enableWebSearch", "true");

    awaitProcessCompletion(
        createProcessInstance(elementTemplateModifier, Map.of("userPrompt", userPrompt)));

    final var recorded = StreamingOpenAiResponsesRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).as("recorded model-call requests").isEqualTo(2);

    // Scan the whole conversation rather than assuming a fixed turn index - mirrors
    // AiAgentJobWorkerAnthropicCodeExecutionTests's conversation-scanning assertion, since the
    // block is replayed as part of whichever request first needs the assistant history replayed.
    assertWebSearchCallRoundTrips(recorded.requests(), webSearchCallId, searchQuery);
  }

  private void assertWebSearchCallRoundTrips(
      List<RecordedChatRequest> requests, String expectedCallId, String expectedQuery) {
    final JsonNode webSearchCallItem =
        requests.stream()
            .flatMap(request -> request.rawInputItems().stream())
            .filter(item -> "web_search_call".equals(item.path("type").asText()))
            .findFirst()
            .orElseThrow(
                () ->
                    new AssertionError(
                        "No web_search_call item found in any recorded request's input[]"));

    assertThat(webSearchCallItem.path("id").asText())
        .as("round-tripped web_search_call id")
        .isEqualTo(expectedCallId);
    assertThat(webSearchCallItem.path("action").path("query").asText())
        .as("round-tripped web_search_call action.query")
        .isEqualTo(expectedQuery);
  }

  // ---------------------------------------------------------------------------
  // Shared plumbing
  // ---------------------------------------------------------------------------

  private static RecordedChatRequest soleRecordedRequest() {
    final var recorded = StreamingOpenAiResponsesRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).as("recorded model-call requests").isEqualTo(1);
    return recorded.lastRequest();
  }
}
