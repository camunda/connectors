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
package io.camunda.connector.e2e.agenticai.aiagent.subprocess.provider.anthropic;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.search.enums.AgentInstanceStatus;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.anthropic.StreamingAnthropicMessagesSseChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.TurnStub;
import io.camunda.connector.e2e.agenticai.assertj.AgentInstanceEngineVerifier;
import io.camunda.connector.e2e.agenticai.assertj.AgentSubProcessResponseAssert;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Anthropic-only e2e coverage for prompt caching's wire behavior - mirrors {@link
 * AgentSubProcessAnthropicReasoningEffortTests}' wiring.
 */
class AgentSubProcessAnthropicPromptCachingTests extends BaseAnthropicSubProcessTest {

  @Test
  void enablePromptCachingAddsCacheControlToTheWire() throws Exception {
    final var userPrompt = "Write a haiku about the sea";

    StreamingAnthropicMessagesSseChatModelStubs.stubConversation(TurnStub.text("A haiku.", 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    final Function<ElementTemplate, ElementTemplate> elementTemplateModifier =
        template ->
            template.property("provider.anthropic.model.parameters.promptCaching.enabled", "true");

    awaitProcessCompletion(
        createProcessInstance(elementTemplateModifier, Map.of("userPrompt", userPrompt)));

    final var request = parseBody(soleRecordedRequest());
    assertThat(request.has("cache_control")).as("top-level cache_control present").isTrue();
    assertThat(request.path("cache_control").path("type").asText())
        .as("cache_control.type")
        .isEqualTo("ephemeral");
  }

  @Test
  void promptCachingDisabledByDefaultLeavesCacheControlOffTheWire() throws Exception {
    final var userPrompt = "Write a haiku about the sea";

    StreamingAnthropicMessagesSseChatModelStubs.stubConversation(TurnStub.text("A haiku.", 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    awaitProcessCompletion(createProcessInstance(Map.of("userPrompt", userPrompt)));

    final var request = parseBody(soleRecordedRequest());
    assertThat(request.has("cache_control"))
        .as("top-level cache_control must not be present when prompt caching is not enabled")
        .isFalse();
  }

  /**
   * Single model call whose usage carries non-zero cache-creation and cache-read token counts, to
   * verify the connector reports them to the Agent Instance API and the engine's aggregated metrics
   * reflect them on read-back. Anthropic is the only provider stub that can dial cache-creation,
   * unlike the OpenAI-based coverage in {@code AgentSubProcessAgentInstanceTests}.
   */
  @Test
  void enablePromptCachingSurfacesCacheTokenCountsInAgentInstanceMetrics() throws Exception {
    final var userPrompt = "Write a haiku about the sea";

    StreamingAnthropicMessagesSseChatModelStubs.stubConversation(
        new StreamingAnthropicMessagesSseChatModelStubs.UsageDetailsTurnStub(
            "A haiku about the endless sea.", 10, 20, 30, 50));
    enqueueUserFeedback(userSatisfiedFeedback());

    final Function<ElementTemplate, ElementTemplate> elementTemplateModifier =
        template ->
            template.property("provider.anthropic.model.parameters.promptCaching.enabled", "true");

    final var zeebeTest =
        awaitProcessCompletion(
            createProcessInstance(elementTemplateModifier, Map.of("userPrompt", userPrompt)));

    final var expectedTokenUsage =
        AgentMetrics.TokenUsage.builder()
            .inputTokenCount(10)
            .outputTokenCount(20)
            .cacheReadTokenCount(50)
            .cacheCreationTokenCount(30)
            .reasoningTokenCount(0)
            .build();
    final var expectedMetrics = new AgentMetrics(1, expectedTokenUsage, 0);
    final var agentInstanceKey = new AtomicLong();
    assertAgentResponse(
        zeebeTest,
        agentResponse -> {
          AgentSubProcessResponseAssert.assertThat(agentResponse)
              .isReady()
              .hasAgentInstanceKey()
              .hasMetrics(expectedMetrics);
          agentInstanceKey.set(agentResponse.context().metadata().agentInstanceKey());
        });

    AgentInstanceEngineVerifier.verify(camundaClient, agentInstanceKey.get())
        .hasStatus(AgentInstanceStatus.COMPLETED)
        .hasMetrics(expectedMetrics)
        .verify();
  }
}
