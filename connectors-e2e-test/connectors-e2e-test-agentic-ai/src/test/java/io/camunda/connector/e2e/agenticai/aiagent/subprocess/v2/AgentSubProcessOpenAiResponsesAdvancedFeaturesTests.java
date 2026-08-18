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
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiResponsesV2RecordedConversation;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiResponsesV2SseChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiResponsesV2SseChatModelStubs.ReasoningTurnStub;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.ToolCallStub;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.TurnStub;
import io.camunda.connector.e2e.agenticai.assertj.AgentSubProcessResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * OpenAI-Responses-only e2e coverage for the {@code effort}/reasoning configuration surface: proves
 * that {@code effort} reaches the wire as {@code reasoning.effort} plus {@code include:
 * ["reasoning.encrypted_content"]}, and that a {@code reasoning} output item carrying {@code
 * encrypted_content} round-trips byte-identical on the follow-up request, positioned before the
 * tool call it accompanied.
 *
 * <p>The round-trip assertion compares raw JSON rather than parsed domain objects or structural
 * map/tree equality: the expected item is canonicalized through the same vendor {@link
 * ObjectMappers#jsonMapper()} round-trip the stub itself performs when materializing the SSE wire
 * body, and the actual item is read straight off the recorded follow-up request's raw {@code
 * input[]} array ({@link
 * OpenAiResponsesV2RecordedConversation.RecordedChatRequest#rawInputItems()}) rather than through
 * the regrouping {@code messages()} parser, which silently skips item kinds it does not model. A
 * field added, dropped, or reordered on either side fails the exact string comparison - unlike a
 * {@code Map}/{@code JsonNode} structural equality check, which is order-insensitive and would not
 * catch a reordering regression.
 */
@SlowTest
class AgentSubProcessOpenAiResponsesAdvancedFeaturesTests
    extends BaseOpenAiResponsesSubProcessTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  private static Function<ElementTemplate, ElementTemplate> effort(String effort) {
    return template -> template.property("provider.openai.api.responses.effort", effort);
  }

  // ---------------------------------------------------------------------------
  // Effort configuration on the wire (negative case)
  // ---------------------------------------------------------------------------

  @Test
  void unsetEffortOmitsReasoningFromTheWire() throws Exception {
    final var userPrompt = "Write a haiku about the sea";
    final var responseText = "A haiku about the endless sea.";

    OpenAiResponsesV2SseChatModelStubs.stubConversation(TurnStub.text(responseText, 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(createProcessInstance(Map.of("userPrompt", userPrompt)));

    final var recorded = OpenAiResponsesV2RecordedConversation.recorded();
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
  // Configured effort on the wire, and byte-identical encrypted-reasoning round-trip
  // ---------------------------------------------------------------------------

  @Test
  void configuredEffortRoundTripsByteIdenticalOnFollowUpRequest() throws Exception {
    final var userPrompt = "Use the superflux tool on 5 and 3.";
    final var reasoningId = "rs_e2e_advfeat_001";
    final var encryptedContent = "gAAAAABo-e2e-encrypted-reasoning-payload-xyz==";
    final var toolCallId = "call_e2e_advfeat_001";
    final var finalMessage = "The superflux calculation of 5 and 3 is 24.";

    OpenAiResponsesV2SseChatModelStubs.stubReasoningConversation(
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

    final var recorded = OpenAiResponsesV2RecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(2);

    final var request = recorded.requests().get(0);
    assertThat(request.reasoningEffort()).as("reasoning.effort").contains("high");
    assertThat(request.include()).as("include[]").contains("reasoning.encrypted_content");

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
   * OpenAiResponsesV2SseChatModelStubs} materializes the SSE wire body for turn 1 - parsing it into
   * the vendor SDK's {@link ResponseOutputItem} union and re-serializing it with the vendor's own
   * {@link ObjectMappers#jsonMapper()} - so the expected value reflects the vendor SDK's actual
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
