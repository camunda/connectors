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

/**
 * Verifies the agent instance state the connector actually persisted on the engine, read back
 * through the query API (get-by-key + history search) from secondary storage.
 *
 * <p>Complements {@link AgentInstanceClientVerifier}: that one proves the ordered sequence of
 * client <em>calls</em> the connector made (a Mockito spy); this one proves those calls committed
 * and accumulated on the broker, which the spy fundamentally cannot show.
 *
 * <p>Secondary storage is eventually consistent, so every assertion is collected up front and
 * evaluated together inside a single {@link org.awaitility.Awaitility} block — instance-level and
 * history-level rows propagate independently, so splitting the await would flake. History reads are
 * filtered to {@link AgentInstanceHistoryCommitStatus#COMMITTED} (what "correctly stored" means)
 * and sorted by the engine-assigned, monotonic {@code historyItemKey} (items in one batched update
 * carry near-identical connector timestamps and would tie on {@code producedAt}).
 */
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

  /** The instance reached its terminal status (the engine transitions to COMPLETED on close). */
  public AgentInstanceEngineVerifier hasStatus(AgentInstanceStatus status) {
    instanceChecks.add(
        instance -> assertThat(instance.getStatus()).as("agent instance status").isEqualTo(status));
    return this;
  }

  /**
   * The accumulated counter metrics stored on the instance equal {@code expected} — the batched
   * turn updates committed and summed on the broker. {@code executionTime} is per-turn and not
   * accumulated, so it is ignored here.
   */
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
          assertThat(metrics.getToolCalls()).as("toolCalls").isEqualTo(expected.toolCalls());
        });
    return this;
  }

  /**
   * The instance definition carries the configured model/provider and a non-empty system prompt.
   */
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

  /** The instance advertises (at least) the given resolved tool names. */
  public AgentInstanceEngineVerifier hasToolsContaining(String... toolNames) {
    instanceChecks.add(
        instance ->
            assertThat(instance.getTools())
                .as("resolved tools")
                .extracting(AgentInstance.Tool::getName)
                .contains(toolNames));
    return this;
  }

  /**
   * The create-time CONFIGURATION history item is present with the configuration the connector sent
   * on {@code create}: model, provider, a non-empty system prompt, the create job's key and lease,
   * and the 1-based first loop iteration. This is the end-to-end proof of issue #8390
   * (configuration sent as a CONFIGURATION item, lease-fenced via jobKey/jobLease) — previously
   * covered only at the Mockito level.
   *
   * <p>The lowest-keyed CONFIGURATION item is the create-time one; further CONFIGURATION items from
   * each {@code applyTurnStart} follow (see {@link #hasConfigurationItemsAtLeast}).
   */
  public AgentInstanceEngineVerifier createdWithConfigurationItem(String model, String provider) {
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
        });
    return this;
  }

  /**
   * The committed non-CONFIGURATION history items carry exactly these roles in {@code
   * historyItemKey} order — the conversation backbone (USER prompt, ASSISTANT responses,
   * TOOL_RESULT inputs).
   *
   * <p>CONFIGURATION items are filtered out first because their count is not stable: {@code create}
   * emits one, and every turn-start re-emits one (the previous turn's configuration fingerprint is
   * not retained across job activations, so the change-detection in {@code
   * CamundaAgentInstanceClient} treats each turn as a change). Surplus CONFIGURATION rows are
   * tolerated per ADR 013 pending per-item dedup (camunda/camunda#58792); their presence is
   * asserted separately via {@link #hasConfigurationItemsAtLeast}.
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

  /**
   * At least {@code min} committed CONFIGURATION items are present: the create-time one plus at
   * least the first turn-start's. See {@link #hasConversationRoles} for why the exact count is not
   * pinned.
   */
  public AgentInstanceEngineVerifier hasConfigurationItemsAtLeast(int min) {
    historyChecks.add(
        history ->
            assertThat(history)
                .as("committed CONFIGURATION history items")
                .filteredOn(item -> item.getRole() == AgentInstanceHistoryRole.CONFIGURATION)
                .hasSizeGreaterThanOrEqualTo(min));
    return this;
  }

  /**
   * A committed TOOL_RESULT history item exists for each of the given tool names (matched on the
   * originating tool call). Order-agnostic and tolerant of the streamed-early duplicate rows ADR
   * 013 documents, so it fits the staggered-tool cases.
   */
  public AgentInstanceEngineVerifier hasToolResultsFor(String... toolNames) {
    historyChecks.add(
        history -> {
          final var resultToolNames =
              history.stream()
                  .filter(item -> item.getRole() == AgentInstanceHistoryRole.TOOL_RESULT)
                  .flatMap(item -> item.getToolCalls().stream())
                  .map(toolCall -> toolCall.getToolName())
                  .toList();
          assertThat(resultToolNames).as("tool result tool names").contains(toolNames);
        });
    return this;
  }

  /** Runs every collected assertion together, awaiting eventual-consistency propagation. */
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
