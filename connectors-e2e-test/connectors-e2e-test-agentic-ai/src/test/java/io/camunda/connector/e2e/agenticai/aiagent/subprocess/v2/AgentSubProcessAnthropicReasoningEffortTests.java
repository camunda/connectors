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
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.anthropic.StreamingAnthropicMessagesSseChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.anthropic.StreamingAnthropicMessagesSseChatModelStubs.RedactedThinkingTurnStub;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.anthropic.StreamingAnthropicMessagesSseChatModelStubs.ThinkingTurnStub;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.ToolCallStub;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.TurnStub;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

/**
 * Native-Anthropic-only e2e coverage for the Anthropic reasoning ({@code thinking}) and {@code
 * effort} configuration surface: proves that the element-template properties, their request-side
 * mapping, and the response-side round-trip all work end to end through the REAL Anthropic SDK
 * types - the vendor SDK's {@code MessageAccumulator} on the response side, and its {@code
 * MessageCreateParams} builder on the request side - not just at the unit level.
 *
 * <p>Uses the v2 element template, {@code provider.anthropic.*} properties, and {@link
 * StreamingAnthropicMessagesSseChatModelStubs} for the streamed SSE response.
 */
class AgentSubProcessAnthropicReasoningEffortTests extends BaseAnthropicNativeSubProcessTest {

  private static final String REASONING_CAPABLE_MODEL = "claude-sonnet-4-6";

  // ---------------------------------------------------------------------------
  // Thinking configuration on the wire
  // ---------------------------------------------------------------------------

  @Test
  void enabledThinkingWithBudgetTokensAppearsOnTheWire() throws Exception {
    final var userPrompt = "Write a haiku about the sea";

    StreamingAnthropicMessagesSseChatModelStubs.stubConversation(TurnStub.text("A haiku.", 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    final Function<ElementTemplate, ElementTemplate> elementTemplateModifier =
        model(REASONING_CAPABLE_MODEL)
            .andThen(
                template ->
                    template
                        .property("provider.anthropic.model.parameters.thinking.mode", "enabled")
                        .property(
                            "provider.anthropic.model.parameters.thinking.budgetTokens", "=2048"));

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

    StreamingAnthropicMessagesSseChatModelStubs.stubConversation(TurnStub.text("A haiku.", 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    final Function<ElementTemplate, ElementTemplate> elementTemplateModifier =
        model(REASONING_CAPABLE_MODEL)
            .andThen(
                template ->
                    template
                        .property("provider.anthropic.model.parameters.thinking.mode", "adaptive")
                        .property(
                            "provider.anthropic.model.parameters.thinking.display", "summarized"));

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

    StreamingAnthropicMessagesSseChatModelStubs.stubConversation(TurnStub.text("A haiku.", 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    final Function<ElementTemplate, ElementTemplate> elementTemplateModifier =
        model(REASONING_CAPABLE_MODEL)
            .andThen(
                template ->
                    template.property(
                        "provider.anthropic.model.parameters.thinking.mode", "disabled"));

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

    StreamingAnthropicMessagesSseChatModelStubs.stubConversation(TurnStub.text("A haiku.", 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    final Function<ElementTemplate, ElementTemplate> elementTemplateModifier =
        model(REASONING_CAPABLE_MODEL)
            .andThen(
                template ->
                    template.property("provider.anthropic.model.parameters.effort", "xhigh"));

    awaitProcessCompletion(
        createProcessInstance(elementTemplateModifier, Map.of("userPrompt", userPrompt)));

    final var request = parseBody(soleRecordedRequest());
    assertThat(request.path("output_config").path("effort").asText())
        .as("output_config.effort")
        .isEqualTo("xhigh");
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

    StreamingAnthropicMessagesSseChatModelStubs.stubThinkingConversation(
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
                        .property("provider.anthropic.model.parameters.thinking.mode", "enabled")
                        .property(
                            "provider.anthropic.model.parameters.thinking.budgetTokens", "=2048"));

    awaitProcessCompletion(
        createProcessInstance(elementTemplateModifier, Map.of("userPrompt", userPrompt)));

    final var loggedRequests = recordedLoggedRequests();
    assertThat(loggedRequests).as("recorded model-call requests").hasSize(2);

    final var secondRequest = parseBody(loggedRequests.get(1));
    assertThinkingBlockRoundTripsBeforeToolResult(secondRequest, thinkingText, signature);
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

    StreamingAnthropicMessagesSseChatModelStubs.stubRedactedThinkingConversation(
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
                        .property("provider.anthropic.model.parameters.thinking.mode", "enabled")
                        .property(
                            "provider.anthropic.model.parameters.thinking.budgetTokens", "=2048"));

    awaitProcessCompletion(
        createProcessInstance(elementTemplateModifier, Map.of("userPrompt", userPrompt)));

    final var loggedRequests = recordedLoggedRequests();
    assertThat(loggedRequests).as("recorded model-call requests").hasSize(2);

    final var secondRequest = parseBody(loggedRequests.get(1));
    assertRedactedThinkingBlockRoundTripsBeforeToolResult(secondRequest, redactedData);
  }

  /**
   * Asserts the second request's assistant message replays the first turn's {@code thinking} block
   * byte-identical (same {@code thinking}/{@code signature}) and positioned before the {@code
   * tool_use} block - proves the response-side {@code ReasoningContent} capture and the
   * request-side round-trip both work end to end through the real accumulator.
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

  /**
   * Mirrors {@link #assertThinkingBlockRoundTripsBeforeToolResult(JsonNode, String, String)} for a
   * {@code redacted_thinking} block (no {@code thinking}/{@code signature}, only opaque {@code
   * data}).
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
}
