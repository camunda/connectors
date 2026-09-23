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
package io.camunda.connector.e2e.agenticai.aiagent.subprocess.provider.mistral;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.assertj.AgentSubProcessResponseAssert;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Mistral-only e2e coverage for Magistral-style chunked reasoning content: unlike OpenAI's own Chat
 * Completions family (input-only {@code reasoning_effort}, see {@code
 * AgentSubProcessOpenAiCompletionsReasoningEffortTests}), a Mistral Magistral response's {@code
 * content} is a chunked array (a {@code thinking} chunk followed by a {@code text} chunk) that the
 * connector parses into a {@link ReasoningContent} plus {@link TextContent}, and must replay
 * byte-faithfully on the next turn.
 */
class AgentSubProcessMistralReasoningTests extends BaseMistralSubProcessTest {

  private static Function<ElementTemplate, ElementTemplate> effort(String effort) {
    return template -> template.property("provider.mistral.parameters.effort", effort);
  }

  // ---------------------------------------------------------------------------
  // Effort configuration on the wire
  // ---------------------------------------------------------------------------

  @Test
  void highEffortAppearsOnTheWireAsReasoningEffort() throws Exception {
    final var userPrompt = "What is 5 + 7?";

    OpenAiCompletionsChatModelStubs.stubConversation(Turn.text("12.", 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    awaitProcessCompletion(createProcessInstance(effort("high"), Map.of("userPrompt", userPrompt)));

    final var request = parseSoleRecordedRequest();
    assertThat(request.path("reasoning_effort").asText()).as("reasoning_effort").isEqualTo("high");
  }

  @Test
  void unsetEffortOmitsReasoningEffortFromTheWire() throws Exception {
    final var userPrompt = "What is 5 + 7?";

    OpenAiCompletionsChatModelStubs.stubConversation(Turn.text("12.", 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    awaitProcessCompletion(createProcessInstance(Map.of("userPrompt", userPrompt)));

    final var request = parseSoleRecordedRequest();
    assertThat(request.has("reasoning_effort"))
        .as("reasoning_effort must not be present when effort is not configured")
        .isFalse();
  }

  // ---------------------------------------------------------------------------
  // Chunked reasoning content parsing
  // ---------------------------------------------------------------------------

  @Test
  void chunkedReasoningContentIsParsedIntoReasoningAndTextContent() throws Exception {
    final var userPrompt = "What is 5 + 7? Think step by step.";
    final var reasoningText = "5 + 7 is a simple addition. 5 + 7 = 12.";
    final var responseText = "The answer is 12.";

    OpenAiCompletionsChatModelStubs.stubConversation(
        Turn.reasoning(reasoningText, responseText, 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(
            createProcessInstance(effort("high"), Map.of("userPrompt", userPrompt)));

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasResponseText(responseText)
                .hasResponseMessageSatisfying(
                    message ->
                        assertThat(message.content())
                            .as("assistant content")
                            .containsExactly(
                                new ReasoningContent(
                                    "mistral",
                                    Map.of("type", "thinking", "closed", true),
                                    reasoningText,
                                    null),
                                TextContent.textContent(responseText))));
  }

  // ---------------------------------------------------------------------------
  // Chunked reasoning content replay on the follow-up turn
  // ---------------------------------------------------------------------------

  @Test
  void chunkedReasoningContentIsReplayedVerbatimOnTheFollowUpTurn() throws Exception {
    final var initialUserPrompt = "What is 5 + 7? Think step by step.";
    final var followUpPrompt = "Now what is that times 2?";
    final var reasoningText = "5 + 7 is a simple addition. 5 + 7 = 12.";
    final var firstResponseText = "The answer is 12.";
    final var secondResponseText = "12 times 2 is 24.";

    OpenAiCompletionsChatModelStubs.stubConversation(
        Turn.reasoning(reasoningText, firstResponseText, 10, 20),
        Turn.text(secondResponseText, 30, 15));
    enqueueUserFeedback(userFollowUpFeedback(followUpPrompt), userSatisfiedFeedback());

    awaitProcessCompletion(
        createProcessInstance(effort("high"), Map.of("userPrompt", initialUserPrompt)));

    final var requests = parseRecordedRequests();
    assertThat(requests).as("recorded model-call requests").hasSize(2);

    final var followUpRequest = requests.get(1);
    final JsonNode replayedAssistantContent =
        findAssistantTextMessage(followUpRequest).path("content");

    assertThat(replayedAssistantContent.isArray())
        .as("replayed assistant content must still be a chunked array, not flattened to text")
        .isTrue();
    assertThat(replayedAssistantContent).hasSize(2);

    final JsonNode thinkingChunk = replayedAssistantContent.get(0);
    assertThat(thinkingChunk.path("type").asText()).isEqualTo("thinking");
    assertThat(thinkingChunk.path("closed").asBoolean()).isTrue();
    assertThat(thinkingChunk.path("thinking").get(0).path("text").asText())
        .isEqualTo(reasoningText);

    final JsonNode textChunk = replayedAssistantContent.get(1);
    assertThat(textChunk.path("type").asText()).isEqualTo("text");
    assertThat(textChunk.path("text").asText()).isEqualTo(firstResponseText);
  }

  /**
   * Finds the assistant message carrying the first turn's replayed reasoning/text content (as
   * opposed to a tool-call-only assistant message, not used by this test) among a request's {@code
   * messages} array.
   */
  private static JsonNode findAssistantTextMessage(JsonNode request) {
    final List<JsonNode> assistantMessages = new ArrayList<>();
    request
        .path("messages")
        .forEach(
            message -> {
              if ("assistant".equals(message.path("role").asText()) && message.has("content")) {
                assistantMessages.add(message);
              }
            });
    assertThat(assistantMessages)
        .as("assistant messages with content in the replayed conversation")
        .hasSize(1);
    return assistantMessages.get(0);
  }
}
