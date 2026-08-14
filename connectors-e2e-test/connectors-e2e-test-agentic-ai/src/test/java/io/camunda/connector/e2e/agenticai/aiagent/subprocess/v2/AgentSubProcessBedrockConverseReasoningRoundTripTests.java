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
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.bedrock.StreamingBedrockConverseEventStreamChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.bedrock.StreamingBedrockConverseEventStreamChatModelStubs.ReasoningTurnStub;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.ToolCallStub;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.TurnStub;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

/**
 * Native-Bedrock-only e2e coverage proving the streamed {@code reasoningContent} block's
 * cryptographic signature survives the round trip: parsed out of the {@code ConverseStream} event
 * sequence by {@code BedrockConverseStreamAssembler}/{@code BedrockConverseResponseConverter} into
 * a domain {@code ReasoningContent}, persisted, then replayed by {@code
 * BedrockConverseContentConverter} onto the wire of the *next* request, ahead of the {@code
 * toolUse} block. Reasoning has no typed configuration on Bedrock, so no {@code bodyProperties}
 * toggle is needed for the block to appear; the stub simply streams one.
 */
class AgentSubProcessBedrockConverseReasoningRoundTripTests
    extends BaseBedrockConverseNativeSubProcessTest {

  @Test
  void roundTripsSignedReasoningContentBlockBeforeToolResultOnFollowUpRequest() throws Exception {
    final var userPrompt = "Use the superflux tool on 5 and 3, thinking it through first.";
    final var reasoningText = "Let me reason through this superflux calculation step by step.";
    final var signature = "sig-e2e-bedrock-abc123==";
    final var toolCallId = "tooluse_01thinkE2E";
    final var satisfiedResponseText = "The superflux calculation of 5 and 3 is 24.";

    StreamingBedrockConverseEventStreamChatModelStubs.stubReasoningConversation(
        new ReasoningTurnStub(
            reasoningText,
            signature,
            List.of(new ToolCallStub(toolCallId, "SuperfluxProduct", "{\"a\": 5, \"b\": 3}")),
            10,
            20),
        TurnStub.text(satisfiedResponseText, 11, 22));
    enqueueUserFeedback(userSatisfiedFeedback());

    awaitProcessCompletion(createProcessInstance(Map.of("userPrompt", userPrompt)));

    final var loggedRequests = recordedLoggedRequests();
    assertThat(loggedRequests).as("recorded model-call requests").hasSize(2);

    final var secondRequest = parseBody(loggedRequests.get(1));
    final var reasoningBlock =
        assertSoleAssistantContentBlockOfKind(secondRequest, "reasoningContent");
    assertThat(reasoningBlock.path("reasoningText").path("text").asText())
        .as("round-tripped reasoning text")
        .isEqualTo(reasoningText);
    assertThat(reasoningBlock.path("reasoningText").path("signature").asText())
        .as("round-tripped reasoning signature")
        .isEqualTo(signature);
  }

  /**
   * Asserts the second request's assistant message replays the first turn's reasoning block
   * positioned before the {@code toolUse} block, exactly as the domain content ordering preserves
   * it, and returns that reasoning block for field-level assertions - proves the response-side
   * {@code ReasoningContent} capture and the request-side round-trip both work end to end through
   * the real {@code sdkFields()} capture/replay codec.
   */
  private JsonNode assertSoleAssistantContentBlockOfKind(JsonNode request, String expectedKind) {
    final var assistantMessage =
        StreamSupport.stream(request.path("messages").spliterator(), false)
            .filter(message -> "assistant".equals(message.path("role").asText()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No assistant message found in second request"));

    final var contentBlocks = assistantMessage.path("content");
    final var contentBlockKinds =
        StreamSupport.stream(contentBlocks.spliterator(), false)
            .map(AgentSubProcessBedrockConverseReasoningRoundTripTests::blockKind)
            .toList();
    assertThat(contentBlockKinds)
        .as("assistant history content block kinds, in order")
        .containsExactly(expectedKind, "toolUse");

    return contentBlocks.get(0).path(expectedKind);
  }

  /** The single field name present on the block, Bedrock's own kind marker (no {@code type}). */
  private static String blockKind(JsonNode block) {
    final var fieldNames = block.fieldNames();
    return fieldNames.hasNext() ? fieldNames.next() : "unknown";
  }
}
