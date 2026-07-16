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
package io.camunda.connector.e2e.agenticai.assertj;

import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContent.textContent;

import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.JobWorkerAgentResponse;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ThrowingConsumer;

public class JobWorkerAgentResponseAssert
    extends AbstractAssert<JobWorkerAgentResponseAssert, JobWorkerAgentResponse> {
  public JobWorkerAgentResponseAssert(JobWorkerAgentResponse actual) {
    super(actual, JobWorkerAgentResponseAssert.class);
  }

  public static JobWorkerAgentResponseAssert assertThat(JobWorkerAgentResponse actual) {
    return new JobWorkerAgentResponseAssert(actual);
  }

  public JobWorkerAgentResponseAssert isReady() {
    return hasState(AgentState.READY);
  }

  public JobWorkerAgentResponseAssert hasState(AgentState expectedState) {
    isNotNull();
    Assertions.assertThat(actual.context().state()).isEqualTo(expectedState);
    return this;
  }

  public JobWorkerAgentResponseAssert hasMetrics(AgentMetrics expectedMetrics) {
    isNotNull();
    Assertions.assertThat(actual.context().metrics()).isEqualTo(expectedMetrics);
    return this;
  }

  public JobWorkerAgentResponseAssert hasReasoningTokensGreaterThanZero() {
    isNotNull();
    Assertions.assertThat(actual.context().metrics().tokenUsage().reasoningTokenCount())
        .as("reasoning token count")
        .isPositive();
    return this;
  }

  public JobWorkerAgentResponseAssert hasCacheCreationTokensGreaterThanZero() {
    isNotNull();
    Assertions.assertThat(actual.context().metrics().tokenUsage().cacheCreationTokenCount())
        .as("cache creation token count")
        .isPositive();
    return this;
  }

  public JobWorkerAgentResponseAssert hasCacheReadTokensGreaterThanZero() {
    isNotNull();
    Assertions.assertThat(actual.context().metrics().tokenUsage().cacheReadTokenCount())
        .as("cache read token count")
        .isPositive();
    return this;
  }

  public JobWorkerAgentResponseAssert hasNoResponseMessage() {
    isNotNull();
    Assertions.assertThat(actual.responseMessage()).isNull();
    return this;
  }

  public JobWorkerAgentResponseAssert hasResponseMessageSatisfying(
      ThrowingConsumer<AssistantMessage> assertions) {
    isNotNull();
    Assertions.assertThat(actual.responseMessage()).isNotNull().satisfies(assertions);
    return this;
  }

  public JobWorkerAgentResponseAssert hasResponseMessageText(String expectedResponseText) {
    isNotNull();
    Assertions.assertThat(actual.responseMessage()).isNotNull();
    Assertions.assertThat(actual.responseMessage().content())
        .hasSize(1)
        .containsExactly(textContent(expectedResponseText));
    return this;
  }

  public JobWorkerAgentResponseAssert hasNoResponseText() {
    isNotNull();
    Assertions.assertThat(actual.responseText()).isNull();
    return this;
  }

  public JobWorkerAgentResponseAssert hasResponseText(String expectedResponseText) {
    isNotNull();
    Assertions.assertThat(actual.responseText()).isEqualTo(expectedResponseText);
    return this;
  }

  public JobWorkerAgentResponseAssert hasResponseTestSatisfying(
      ThrowingConsumer<String> assertions) {
    isNotNull();
    Assertions.assertThat(actual.responseText()).isNotNull().satisfies(assertions);
    return this;
  }

  public JobWorkerAgentResponseAssert hasNoResponseJson() {
    isNotNull();
    Assertions.assertThat(actual.responseJson()).isNull();
    return this;
  }

  public JobWorkerAgentResponseAssert hasResponseJson(Object expectedResponseJson) {
    isNotNull();
    Assertions.assertThat(actual.responseJson()).isNotNull().isEqualTo(expectedResponseJson);
    return this;
  }

  public JobWorkerAgentResponseAssert hasResponseJsonSatisfying(
      ThrowingConsumer<Object> assertions) {
    isNotNull();
    Assertions.assertThat(actual.responseJson()).isNotNull().satisfies(assertions);
    return this;
  }

  public JobWorkerAgentResponseAssert hasAgentInstanceKey() {
    isNotNull();
    Assertions.assertThat(actual.context()).isNotNull();
    Assertions.assertThat(actual.context().metadata()).isNotNull();
    Assertions.assertThat(actual.context().metadata().agentInstanceKey()).isNotNull().isPositive();
    return this;
  }

  public JobWorkerAgentResponseAssert hasLastIterationKey(int expectedLastIterationKey) {
    isNotNull();
    Assertions.assertThat(actual.context()).isNotNull();
    Assertions.assertThat(actual.context().metadata()).isNotNull();
    Assertions.assertThat(actual.context().metadata().lastIterationKey())
        .isEqualTo(expectedLastIterationKey);
    return this;
  }

  /**
   * Scans the WHOLE persisted conversation (not just {@code responseMessage()}) for a provider
   * content block of the given type. Some providers can emit a server-tool result block in an
   * interim assistant turn rather than the final response message (e.g. Anthropic's {@code
   * pause_turn} continuation for web search), so asserting only on {@code responseMessage()} would
   * be flaky for those providers. Requires the in-process conversation store (context.
   * conversation() is an {@link InProcessConversationContext}).
   */
  public JobWorkerAgentResponseAssert hasProviderContentBlockOfTypeInConversation(
      String provider, String blockType) {
    isNotNull();
    Assertions.assertThat(actual.context()).isNotNull();
    Assertions.assertThat(actual.context().conversation())
        .isInstanceOf(InProcessConversationContext.class);
    var conversation = (InProcessConversationContext) actual.context().conversation();
    var providerBlocks =
        conversation.messages().stream()
            .filter(m -> m instanceof AssistantMessage)
            .map(m -> (AssistantMessage) m)
            .flatMap(m -> m.content().stream())
            .filter(c -> c instanceof ProviderContent)
            .map(c -> (ProviderContent) c)
            .toList();
    Assertions.assertThat(providerBlocks)
        .as("provider content blocks across the whole conversation")
        .anySatisfy(
            pc -> {
              Assertions.assertThat(pc.provider()).isEqualTo(provider);
              Assertions.assertThat(pc.blockType()).isEqualTo(blockType);
            });
    return this;
  }
}
