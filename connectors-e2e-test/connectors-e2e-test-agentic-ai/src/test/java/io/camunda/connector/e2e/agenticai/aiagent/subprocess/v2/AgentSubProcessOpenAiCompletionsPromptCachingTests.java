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

import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsV2SseChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsV2SseChatModelStubs.UsageDetailsTurnStub;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.TurnStub;
import io.camunda.connector.e2e.agenticai.assertj.AgentSubProcessResponseAssert;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * OpenAI-Chat-Completions-only e2e coverage for prompt caching's wire behavior - mirrors {@link
 * AgentSubProcessAnthropicPromptCachingTests}' wiring, adapted to what OpenAI can actually report.
 *
 * <p>Unlike Anthropic, OpenAI's prompt caching is fully automatic server-side: there is no
 * request-side opt-in (no {@code promptCaching.enabled} template property, no {@code cache_control}
 * on the wire - see {@code OpenAiCompletionsRequestConverter}, which has no caching-related method
 * at all) and, critically, <b>no cache-write/cache-creation metric is ever reported</b> - only a
 * cache-*read* count ({@code prompt_tokens_details.cached_tokens}) on a cache hit. So this class
 * asserts only the response side: that a non-zero {@code cached_tokens} value surfaces into {@link
 * AgentMetrics.TokenUsage#cacheReadTokenCount()}, and that {@link
 * AgentMetrics.TokenUsage#cacheCreationTokenCount()} stays {@code 0} in both the hit and the miss
 * case - never asserting a cache-creation count as non-zero, since OpenAI can never report one (see
 * {@code OpenAiCompletionsResponseConverter#toTokenUsage}).
 */
class AgentSubProcessOpenAiCompletionsPromptCachingTests extends BaseOpenAiSubProcessTest {

  @Test
  void cacheReadTokensSurfaceIntoTokenUsageOnACacheHit() throws Exception {
    final var userPrompt = "Write a haiku about the sea";
    final var responseText = "A haiku about the endless sea.";

    OpenAiCompletionsV2SseChatModelStubs.stubConversation(
        new UsageDetailsTurnStub(responseText, 10, 20, 15L, 0L));
    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(createProcessInstance(Map.of("userPrompt", userPrompt)));

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasResponseText(responseText)
                .hasMetrics(new AgentMetrics(1, new AgentMetrics.TokenUsage(10, 20, 15, 0, 0), 0)));
  }

  @Test
  void noCacheHitLeavesCacheReadTokenCountAtZero() throws Exception {
    final var userPrompt = "Write a haiku about the sea";
    final var responseText = "A haiku about the endless sea.";

    OpenAiCompletionsV2SseChatModelStubs.stubConversation(TurnStub.text(responseText, 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(createProcessInstance(Map.of("userPrompt", userPrompt)));

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasResponseText(responseText)
                .hasMetrics(new AgentMetrics(1, new AgentMetrics.TokenUsage(10, 20), 0)));
  }
}
