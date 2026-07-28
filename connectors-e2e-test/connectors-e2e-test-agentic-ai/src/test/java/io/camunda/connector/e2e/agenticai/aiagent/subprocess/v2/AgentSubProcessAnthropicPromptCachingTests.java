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

import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.anthropic.StreamingAnthropicMessagesSseChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.TurnStub;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Native-Anthropic-only e2e coverage for the {@code
 * provider.anthropic.model.parameters.promptCaching.enabled} element-template property: proves that
 * enabling prompt caching actually adds the top-level {@code cache_control: {"type": "ephemeral"}}
 * field to the recorded wire request, and that leaving it unset/off leaves the field off the wire.
 *
 * <p>Uses the v2 element template, {@code provider.anthropic.*} properties, and {@link
 * StreamingAnthropicMessagesSseChatModelStubs} for the streamed SSE response - mirrors {@link
 * AgentSubProcessAnthropicReasoningEffortTests}' wiring.
 */
class AgentSubProcessAnthropicPromptCachingTests extends BaseAnthropicNativeSubProcessTest {

  private static final String MODEL = "claude-sonnet-4-6";

  @Override
  protected String defaultModel() {
    return MODEL;
  }

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
}
