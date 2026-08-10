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

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.core.ObjectMappers;
import com.openai.models.responses.ResponseOutputItem;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.NativeOpenAiResponsesRecordedConversation;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.NativeOpenAiResponsesSseChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.NativeOpenAiResponsesSseChatModelStubs.ReasoningTurnStub;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.NativeOpenAiResponsesSseChatModelStubs.ServerToolTurnStub;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.ToolCallStub;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.TurnStub;
import io.camunda.connector.e2e.agenticai.assertj.AgentSubProcessResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Native-OpenAI-Responses-only e2e coverage for the {@code effort}/reasoning configuration surface
 * and the two round-trip guarantees the design spec calls out as load-bearing: a {@code reasoning}
 * output item carrying {@code encrypted_content} must reappear byte-identical on the follow-up
 * request, and an unmapped server-tool output item ({@code web_search_call}) must be captured and
 * replayed verbatim as {@code ProviderContent}. All three witnesses drive the real vendor SDK end
 * to end - the {@code ResponseAccumulator} on the response side ({@code
 * OpenAiResponsesStreamAssembler#accumulating()}, wired by default) and {@code
 * ResponseCreateParams}' own request construction on the request side - through the v2 element
 * template and {@code provider.openai.*} properties, mirroring {@code
 * AgentSubProcessAnthropicReasoningEffortTests} for the OpenAI Responses API family.
 *
 * <p>Placed alongside {@link BaseAgentSubProcessV2Test} (rather than under {@code
 * aiagent/wiremock/openai}, where the Responses fixture/stub/adapter classes it consumes live)
 * because that base class is package-private, matching every other native-provider v2 e2e test in
 * this module (e.g. {@code AgentSubProcessAnthropicReasoningEffortTests}, {@code
 * AgentSubProcessCustomProviderToolCallingTests}).
 *
 * <p>Witness 2 (the encrypted-reasoning round-trip) and witness 3 (the {@code ProviderContent}
 * round-trip) assert on raw JSON rather than parsed domain objects or structural map/tree equality:
 * the expected item is canonicalized through the same vendor {@link ObjectMappers#jsonMapper()}
 * round-trip the stub itself performs when materializing the SSE wire body ({@code readValue} then
 * {@code writeValueAsString}), and the actual item is read straight off the recorded follow-up
 * request's raw {@code input[]} array ({@link
 * NativeOpenAiResponsesRecordedConversation.RecordedChatRequest#rawInputItems()}) rather than
 * through the regrouping {@code messages()} parser, which silently skips item kinds it does not
 * model. A field added, dropped, or reordered on either side fails the exact string comparison -
 * unlike a {@code Map}/{@code JsonNode} structural equality check, which is order-insensitive and
 * would not catch a reordering regression.
 */
@SlowTest
class AgentSubProcessNativeOpenAiResponsesAdvancedFeaturesTests extends BaseAgentSubProcessV2Test {

  private static final String DEFAULT_MODEL = "test-model";
  private static final ObjectMapper JSON = new ObjectMapper();

  @Override
  protected Function<ElementTemplate, ElementTemplate> providerConfigurer() {
    return this::configureOpenAiResponsesBackend;
  }

  /** Mirrors {@code NativeOpenAiResponsesWireFormatFixture#configureProvider}. */
  private ElementTemplate configureOpenAiResponsesBackend(ElementTemplate template) {
    return template
        .property("provider.type", "openai")
        .property("provider.openai.api.type", "responses")
        .property("provider.openai.backend.type", "custom")
        .property("provider.openai.backend.custom.endpoint", wireMock.getHttpBaseUrl() + "/v1")
        .property("provider.openai.backend.custom.authentication.type", "apiKey")
        .property("provider.openai.backend.custom.authentication.apiKey", "dummy")
        .property("provider.openai.model.model", DEFAULT_MODEL);
  }

  private static Function<ElementTemplate, ElementTemplate> effort(String effort) {
    return template -> template.property("provider.openai.api.responses.effort", effort);
  }

  // ---------------------------------------------------------------------------
  // Witness 1: effort configuration on the wire
  // ---------------------------------------------------------------------------

  @Test
  void configuredEffortAppearsAsReasoningEffortOnTheWire() throws Exception {
    final var userPrompt = "Write a haiku about the sea";
    final var responseText = "A haiku about the endless sea.";

    NativeOpenAiResponsesSseChatModelStubs.stubConversation(TurnStub.text(responseText, 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(
            createProcessInstance(effort("high"), Map.of("userPrompt", userPrompt)));

    final var recorded = NativeOpenAiResponsesRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(1);

    final var request = recorded.lastRequest();
    assertThat(request.reasoningEffort()).as("reasoning.effort").contains("high");
    assertThat(request.include()).as("include[]").contains("reasoning.encrypted_content");

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasResponseText(responseText));
  }

  @Test
  void unsetEffortOmitsReasoningFromTheWire() throws Exception {
    final var userPrompt = "Write a haiku about the sea";
    final var responseText = "A haiku about the endless sea.";

    NativeOpenAiResponsesSseChatModelStubs.stubConversation(TurnStub.text(responseText, 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(createProcessInstance(Map.of("userPrompt", userPrompt)));

    final var recorded = NativeOpenAiResponsesRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(1);

    final var request = recorded.lastRequest();
    assertThat(request.reasoningEffort()).as("reasoning.effort").isEmpty();
    assertThat(request.include()).as("include[]").isEmpty();

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasResponseText(responseText));
  }

  // ---------------------------------------------------------------------------
  // Witness 2: byte-identical encrypted-reasoning round-trip
  // ---------------------------------------------------------------------------

  @Test
  void reasoningItemWithEncryptedContentRoundTripsByteIdenticalOnFollowUpRequest()
      throws Exception {
    final var userPrompt = "Use the superflux tool on 5 and 3.";
    final var reasoningId = "rs_e2e_advfeat_001";
    final var encryptedContent = "gAAAAABo-e2e-encrypted-reasoning-payload-xyz==";
    final var toolCallId = "call_e2e_advfeat_001";
    final var finalMessage = "The superflux calculation of 5 and 3 is 24.";

    NativeOpenAiResponsesSseChatModelStubs.stubReasoningConversation(
        new ReasoningTurnStub(
            reasoningId,
            encryptedContent,
            List.of(new ToolCallStub(toolCallId, "SuperfluxProduct", "{\"a\": 5, \"b\": 3}")),
            10,
            20),
        TurnStub.text(finalMessage, 11, 22));
    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(
            createProcessInstance(effort("high"), Map.of("userPrompt", userPrompt)));

    final var recorded = NativeOpenAiResponsesRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(2);

    final var followUpRequest = recorded.requests().get(1);
    final var rawItems = followUpRequest.rawInputItems();

    final var actualReasoningItem = soleItemOfType(rawItems, "reasoning");
    final var expectedReasoningItemJson =
        "{\"type\":\"reasoning\",\"id\":"
            + JSON.writeValueAsString(reasoningId)
            + ",\"encrypted_content\":"
            + JSON.writeValueAsString(encryptedContent)
            + ",\"summary\":[]}";

    assertThat(JSON.writeValueAsString(actualReasoningItem))
        .as("reasoning item replayed byte-identical on the follow-up request")
        .isEqualTo(canonicalOutputItemJson(expectedReasoningItemJson));

    // The reasoning item must still precede the function_call item it originally accompanied.
    assertThat(indexOfType(rawItems, "reasoning"))
        .as("reasoning item positioned before its accompanying function_call")
        .isLessThan(indexOfType(rawItems, "function_call"));

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasResponseText(finalMessage));
  }

  // ---------------------------------------------------------------------------
  // Witness 3: ProviderContent round-trip for an unmapped server-tool item
  // ---------------------------------------------------------------------------

  @Test
  void unmappedServerToolItemRoundTripsVerbatimAsProviderContentOnFollowUpRequest()
      throws Exception {
    final var userPrompt = "What is the weather in Berlin right now?";
    final var assistantText = "Let me check that for you.";
    final var searchQuery = "current weather in Berlin";
    final var webSearchCallId = "ws_e2e_advfeat_001";
    final var followUpPrompt = "And in Munich?";
    final var finalMessage = "It's sunny and 22 degrees in Berlin right now.";

    NativeOpenAiResponsesSseChatModelStubs.stubServerToolConversation(
        new ServerToolTurnStub(assistantText, webSearchCallId, searchQuery, 10, 20),
        TurnStub.text(finalMessage, 11, 22));
    // The server-tool turn carries no client tool call (it is resolved server-side by OpenAI
    // itself), so the agent completes and awaits user feedback after turn 1 - a follow-up
    // (unsatisfied) feedback is what drives the second model call that replays turn 1's content.
    enqueueUserFeedback(userFollowUpFeedback(followUpPrompt), userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(createProcessInstance(Map.of("userPrompt", userPrompt)));

    final var recorded = NativeOpenAiResponsesRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(2);

    final var followUpRequest = recorded.requests().get(1);
    final var rawItems = followUpRequest.rawInputItems();

    final var actualWebSearchItem = soleItemOfType(rawItems, "web_search_call");
    final var expectedWebSearchItemJson =
        "{\"type\":\"web_search_call\",\"id\":"
            + JSON.writeValueAsString(webSearchCallId)
            + ",\"status\":\"completed\",\"action\":{\"type\":\"search\",\"query\":"
            + JSON.writeValueAsString(searchQuery)
            + "}}";

    assertThat(JSON.writeValueAsString(actualWebSearchItem))
        .as("web_search_call item replayed verbatim as ProviderContent on the follow-up request")
        .isEqualTo(canonicalOutputItemJson(expectedWebSearchItemJson));

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasResponseText(finalMessage));
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private static JsonNode soleItemOfType(List<JsonNode> items, String type) {
    final var matches =
        items.stream().filter(item -> type.equals(item.path("type").asText())).toList();
    assertThat(matches).as("input items of type '%s'", type).hasSize(1);
    return matches.get(0);
  }

  private static int indexOfType(List<JsonNode> items, String type) {
    for (int i = 0; i < items.size(); i++) {
      if (type.equals(items.get(i).path("type").asText())) {
        return i;
      }
    }
    throw new AssertionError("No input item of type '" + type + "' found");
  }

  /**
   * Canonicalizes a hand-written response output item's JSON the same way {@code
   * NativeOpenAiResponsesSseChatModelStubs} materializes the SSE wire body for turn 1 - parsing it
   * into the vendor SDK's {@link ResponseOutputItem} union and re-serializing it with the vendor's
   * own {@link ObjectMappers#jsonMapper()} - so the expected value reflects the vendor SDK's actual
   * canonical field order rather than whatever order this literal happens to be written in. The SDK
   * mapper always resolves a given field set to the same canonical order regardless of input order,
   * so this literal's field order is deliberately independent of the stub's own private
   * construction.
   */
  private static String canonicalOutputItemJson(String rawItemJson) throws Exception {
    return ObjectMappers.jsonMapper()
        .writeValueAsString(
            ObjectMappers.jsonMapper().readValue(rawItemJson, ResponseOutputItem.class));
  }
}
