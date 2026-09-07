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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.AgentInstanceHistoryToolCall;
import io.camunda.client.api.search.enums.AgentInstanceHistoryCommitStatus;
import io.camunda.client.api.search.enums.AgentInstanceHistoryRole;
import io.camunda.client.api.search.enums.AgentInstanceStatus;
import io.camunda.client.api.search.response.AgentInstance;
import io.camunda.client.api.search.response.AgentInstanceHistory;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Reads an agent instance back through the query API and asserts its persisted state. */
public class AgentInstanceEngineVerifier {

  private final CamundaClient client;
  private final long agentInstanceKey;
  private final List<Consumer<AgentInstance>> instanceChecks = new ArrayList<>();
  private final List<Consumer<List<AgentInstanceHistory>>> historyChecks = new ArrayList<>();

  private AgentInstanceEngineVerifier(CamundaClient client, long agentInstanceKey) {
    this.client = client;
    this.agentInstanceKey = agentInstanceKey;
  }

  public static AgentInstanceEngineVerifier verify(CamundaClient client, long agentInstanceKey) {
    return new AgentInstanceEngineVerifier(client, agentInstanceKey);
  }

  public AgentInstanceEngineVerifier hasStatus(AgentInstanceStatus status) {
    instanceChecks.add(
        instance -> assertThat(instance.getStatus()).as("agent instance status").isEqualTo(status));
    return this;
  }

  public AgentInstanceEngineVerifier hasMetrics(AgentMetrics expected) {
    instanceChecks.add(
        instance -> {
          final var metrics = instance.getMetrics();
          assertThat(metrics.getModelCalls()).as("modelCalls").isEqualTo(expected.modelCalls());
          assertThat(metrics.getInputTokens())
              .as("inputTokens")
              .isEqualTo(expected.tokenUsage().inputTokenCount());
          assertThat(metrics.getOutputTokens())
              .as("outputTokens")
              .isEqualTo(expected.tokenUsage().outputTokenCount());
          assertThat(metrics.getReasoningTokenCount())
              .as("reasoningTokenCount")
              .isEqualTo(expected.tokenUsage().reasoningTokenCount());
          assertThat(metrics.getCacheCreationTokenCount())
              .as("cacheCreationTokenCount")
              .isEqualTo(expected.tokenUsage().cacheCreationTokenCount());
          assertThat(metrics.getCacheReadTokenCount())
              .as("cacheReadTokenCount")
              .isEqualTo(expected.tokenUsage().cacheReadTokenCount());
          assertThat(metrics.getToolCalls()).as("toolCalls").isEqualTo(expected.toolCalls());
        });
    return this;
  }

  public AgentInstanceEngineVerifier hasDefinition(String model, String provider) {
    instanceChecks.add(
        instance -> {
          final var definition = instance.getDefinition();
          assertThat(definition.getModel()).as("definition model").isEqualTo(model);
          assertThat(definition.getProvider()).as("definition provider").isEqualTo(provider);
          assertThat(definition.getSystemPrompt()).as("definition system prompt").isNotEmpty();
        });
    return this;
  }

  public AgentInstanceEngineVerifier hasToolsContaining(String... toolNames) {
    instanceChecks.add(
        instance ->
            assertThat(instance.getTools())
                .as("resolved tools")
                .extracting(AgentInstance.Tool::getName)
                .contains(toolNames));
    return this;
  }

  /** Asserts the create-time CONFIGURATION item, i.e. the lowest-keyed one. */
  public AgentInstanceEngineVerifier createdWithConfigurationItem(
      String model, String provider, int maxModelCalls) {
    historyChecks.add(
        history -> {
          final var configItem =
              history.stream()
                  .filter(item -> item.getRole() == AgentInstanceHistoryRole.CONFIGURATION)
                  .findFirst()
                  .orElseThrow(
                      () -> new AssertionError("no CONFIGURATION history item on the instance"));
          assertThat(configItem.getModel()).as("config item model").isEqualTo(model);
          assertThat(configItem.getProvider()).as("config item provider").isEqualTo(provider);
          assertThat(configItem.getSystemPrompt()).as("config item system prompt").isNotEmpty();
          assertThat(configItem.getLoopIteration()).as("config item loop iteration").isEqualTo(1);
          assertThat(configItem.getJobKey()).as("config item job key").isPositive();
          assertThat(configItem.getJobLease()).as("config item job lease").isNotBlank();
          assertThat(configItem.getLimits().getMaxModelCalls())
              .as("config item max model calls")
              .isEqualTo(maxModelCalls);
          assertThat(configItem.getLimits().getMaxTokens())
              .as("config item max tokens")
              .isEqualTo(-1);
          assertThat(configItem.getLimits().getMaxToolCalls())
              .as("config item max tool calls")
              .isEqualTo(-1);
        });
    return this;
  }

  /**
   * Asserts the non-CONFIGURATION roles in {@code historyItemKey} order. CONFIGURATION items are
   * excluded because {@code applyTurnStart} emits one only for the first turn or when the
   * configuration changed since the previous turn, not on every turn-start.
   */
  public AgentInstanceEngineVerifier hasConversationRoles(AgentInstanceHistoryRole... roles) {
    historyChecks.add(
        history ->
            assertThat(history)
                .as("committed conversation-backbone roles (CONFIGURATION filtered out)")
                .filteredOn(item -> item.getRole() != AgentInstanceHistoryRole.CONFIGURATION)
                .extracting(AgentInstanceHistory::getRole)
                .containsExactly(roles));
    return this;
  }

  public AgentInstanceEngineVerifier hasConfigurationItemsAtLeast(int min) {
    historyChecks.add(
        history ->
            assertThat(history)
                .as("committed CONFIGURATION history items")
                .filteredOn(item -> item.getRole() == AgentInstanceHistoryRole.CONFIGURATION)
                .hasSizeGreaterThanOrEqualTo(min));
    return this;
  }

  /** Asserts a committed TOOL_RESULT exists for each named tool, order-agnostic. */
  public AgentInstanceEngineVerifier hasToolResultsFor(String... toolNames) {
    historyChecks.add(
        history -> {
          final var resultToolNames =
              history.stream()
                  .filter(item -> item.getRole() == AgentInstanceHistoryRole.TOOL_RESULT)
                  .flatMap(item -> item.getToolCalls().stream())
                  .map(AgentInstanceHistoryToolCall::getToolName)
                  .toList();
          assertThat(resultToolNames).as("tool result tool names").contains(toolNames);
        });
    return this;
  }

  /** Awaits eventual consistency and evaluates all collected checks together. */
  public void verify() {
    await()
        .alias("agent instance state via query API")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var instance = client.newAgentInstanceGetRequest(agentInstanceKey).execute();
              final var history =
                  client
                      .newAgentInstanceHistorySearchRequest(agentInstanceKey)
                      .filter(f -> f.commitStatus(AgentInstanceHistoryCommitStatus.COMMITTED))
                      .sort(s -> s.historyItemKey().asc())
                      .execute()
                      .items();
              instanceChecks.forEach(check -> check.accept(instance));
              historyChecks.forEach(check -> check.accept(history));
            });
  }
}
