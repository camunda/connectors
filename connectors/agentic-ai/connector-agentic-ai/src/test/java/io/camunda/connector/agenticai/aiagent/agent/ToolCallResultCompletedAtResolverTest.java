/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;
import org.junit.jupiter.api.Test;

class ToolCallResultCompletedAtResolverTest {

  private static final OffsetDateTime T1 = OffsetDateTime.parse("2026-07-02T10:00:00Z");
  private static final OffsetDateTime T2 = OffsetDateTime.parse("2026-07-02T10:00:05Z");
  private static final OffsetDateTime T3 = OffsetDateTime.parse("2026-07-02T10:00:10Z");

  @Test
  void keepsEngineTimestampWhenPresent() {
    // given
    ToolCallResultCompletedAtResolver resolver = resolverReturning(T2);
    ToolCallResult result =
        ToolCallResult.builder().id("call-1").name("tool").completedAt(T1).build();

    // when
    var resolved = resolver.resolve(AgentContext.empty(), List.of(result));

    // then: A is kept as-is; the worker clock is never consulted
    assertThat(resolved.toolCallResults()).singleElement().extracting("completedAt").isEqualTo(T1);
  }

  @Test
  void stampsWorkerObservedTimeWhenEngineTimestampMissing() {
    // given
    ToolCallResultCompletedAtResolver resolver = resolverReturning(T1);
    ToolCallResult result = ToolCallResult.builder().id("call-1").name("tool").build();

    // when
    var resolved = resolver.resolve(AgentContext.empty(), List.of(result));

    // then
    assertThat(resolved.toolCallResults()).singleElement().extracting("completedAt").isEqualTo(T1);
  }

  @Test
  void keepsEarliestWorkerObservedTimeAcrossRepeatedNoOpJobs() {
    // given: job 1 sees only the fast result (still missing the slow one)
    ToolCallResultCompletedAtResolver resolver = resolverReturning(T1, T3);
    ToolCallResult fastResult = ToolCallResult.builder().id("fast").name("tool").build();
    ToolCallResult slowResult = ToolCallResult.builder().id("slow").name("tool").build();

    var firstJob = resolver.resolve(AgentContext.empty(), List.of(fastResult));
    assertThat(firstJob.toolCallResults()).singleElement().extracting("completedAt").isEqualTo(T1);

    // when: job 2 (persisted agentContext from job 1) finally sees both results, at T3
    var secondJob = resolver.resolve(firstJob.agentContext(), List.of(fastResult, slowResult));

    // then: the fast result keeps its originally observed time (T1), not T3 (when the slow
    // result -- and thus the whole batch -- finally arrived); the slow result is observed fresh
    var fastResolved =
        secondJob.toolCallResults().stream().filter(r -> "fast".equals(r.id())).findFirst().get();
    var slowResolved =
        secondJob.toolCallResults().stream().filter(r -> "slow".equals(r.id())).findFirst().get();
    assertThat(fastResolved.completedAt()).isEqualTo(T1);
    assertThat(slowResolved.completedAt()).isEqualTo(T3);
  }

  @Test
  void prunesStaleEntriesNotPresentInCurrentBatch() {
    // given: job 1 observes "fast", persisting it into agentContext
    ToolCallResultCompletedAtResolver resolver = resolverReturning(T1, T2);
    ToolCallResult fastResult = ToolCallResult.builder().id("fast").name("tool").build();
    var firstJob = resolver.resolve(AgentContext.empty(), List.of(fastResult));

    // when: the next round starts fresh with an unrelated result id (previous round consumed)
    ToolCallResult nextRoundResult = ToolCallResult.builder().id("next-round").name("tool").build();
    var secondJob = resolver.resolve(firstJob.agentContext(), List.of(nextRoundResult));

    // then: "fast" must not linger and get accidentally reused if its id ever recurred
    assertThat(secondJob.toolCallResults()).singleElement().extracting("completedAt").isEqualTo(T2);
    assertThat(secondJob.agentContext().properties().values())
        .as("stale entries from a consumed round must not accumulate forever")
        .noneMatch(value -> value.toString().contains(T1.toString()));
  }

  @Test
  void stampsEventResultsWithoutAnIdEveryTime() {
    // given
    ToolCallResultCompletedAtResolver resolver = resolverReturning(T1);
    ToolCallResult eventResult = ToolCallResult.builder().content("event data").build();

    // when
    var resolved = resolver.resolve(AgentContext.empty(), List.of(eventResult));

    // then
    assertThat(resolved.toolCallResults()).singleElement().extracting("completedAt").isEqualTo(T1);
  }

  @Test
  void doesNotModifyAgentContextWhenNothingNeededResolving() {
    // given: every result already carries an engine timestamp
    ToolCallResultCompletedAtResolver resolver = resolverReturning(T1);
    ToolCallResult result =
        ToolCallResult.builder().id("call-1").name("tool").completedAt(T2).build();
    AgentContext agentContext = AgentContext.empty();

    // when
    var resolved = resolver.resolve(agentContext, List.of(result));

    // then
    assertThat(resolved.agentContext()).isEqualTo(agentContext);
  }

  @Test
  void doesNotModifyAgentContextForEmptyBatch() {
    // given
    ToolCallResultCompletedAtResolver resolver = resolverReturning(T1);
    AgentContext agentContext = AgentContext.empty();

    // when
    var resolved = resolver.resolve(agentContext, List.of());

    // then
    assertThat(resolved.agentContext()).isEqualTo(agentContext);
    assertThat(resolved.toolCallResults()).isEmpty();
  }

  private static ToolCallResultCompletedAtResolver resolverReturning(OffsetDateTime... instants) {
    Queue<OffsetDateTime> queue = new LinkedBlockingDeque<>(List.of(instants));
    return new ToolCallResultCompletedAtResolver(queue::poll);
  }
}
