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

import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsV2SseChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsV2SseChatModelStubs.UsageDetailsTurnStub;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.TurnStub;
import io.camunda.connector.e2e.agenticai.assertj.AgentSubProcessResponseAssert;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * OpenAI-Chat-Completions-only e2e coverage for the {@code effort} configuration surface: the
 * Completions family is input-only via {@code reasoning_effort} - no reasoning content is ever
 * emitted, only the {@code reasoning_tokens} count. Confirms the {@code effort} dial reaches the
 * wire, and that {@code TokenUsage} surfaces the reasoning-token count regardless.
 */
class AgentSubProcessOpenAiCompletionsReasoningEffortTests
    extends BaseOpenAiCompletionsSubProcessTest {

  private static Function<ElementTemplate, ElementTemplate> effort(String effort) {
    return template -> template.property("provider.openai.api.completions.effort", effort);
  }

  // ---------------------------------------------------------------------------
  // Effort configuration on the wire
  // ---------------------------------------------------------------------------

  @Test
  void xhighEffortAppearsOnTheWireAsReasoningEffort() throws Exception {
    final var userPrompt = "Write a haiku about the sea";

    OpenAiCompletionsV2SseChatModelStubs.stubConversation(TurnStub.text("A haiku.", 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    awaitProcessCompletion(
        createProcessInstance(effort("xhigh"), Map.of("userPrompt", userPrompt)));

    final var request = parseSoleRecordedRequest();
    assertThat(request.path("reasoning_effort").asText()).as("reasoning_effort").isEqualTo("xhigh");
  }

  @Test
  void unsetEffortOmitsReasoningEffortFromTheWire() throws Exception {
    final var userPrompt = "Write a haiku about the sea";

    OpenAiCompletionsV2SseChatModelStubs.stubConversation(TurnStub.text("A haiku.", 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    awaitProcessCompletion(createProcessInstance(Map.of("userPrompt", userPrompt)));

    final var request = parseSoleRecordedRequest();
    assertThat(request.has("reasoning_effort"))
        .as("reasoning_effort must not be present when effort is not configured")
        .isFalse();
  }

  // ---------------------------------------------------------------------------
  // Reasoning-token accounting: no reasoning content, but the token count still surfaces
  // ---------------------------------------------------------------------------

  @Test
  void reasoningTokensSurfaceIntoTokenUsageWithoutAnyReasoningContent() throws Exception {
    final var userPrompt = "Write a haiku about the sea";
    final var responseText = "A haiku about the endless sea.";

    OpenAiCompletionsV2SseChatModelStubs.stubConversation(
        new UsageDetailsTurnStub(responseText, 10, 20, 0L, 7L));
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
                // No ReasoningContent is ever emitted by the Completions converter - only the
                // token count is surfaced.
                .hasResponseMessageSatisfying(
                    message ->
                        assertThat(message.content())
                            .as("assistant content - no ReasoningContent is ever emitted")
                            .noneMatch(ReasoningContent.class::isInstance))
                .hasMetrics(new AgentMetrics(1, new AgentMetrics.TokenUsage(10, 20, 0, 0, 7), 0)));
  }
}
