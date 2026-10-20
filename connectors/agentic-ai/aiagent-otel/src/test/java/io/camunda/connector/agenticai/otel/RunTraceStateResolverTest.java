/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.otel;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.api.lifecycle.AgentFlavor;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.AgentJobInfo;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.AgentJobKind;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.JobStarted;
import io.camunda.connector.agenticai.otel.RunTraceState.Status;
import io.camunda.connector.agenticai.otel.RunTraceStateResolver.Resolution;
import java.time.Instant;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class RunTraceStateResolverTest {

  private static final long PROCESS_INSTANCE_KEY = 2251799813685249L;
  private static final long AGENT_INSTANCE_KEY = 2251799813685260L;
  private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String SPAN_ID = "00f067aa0ba902b7";

  private final RunTraceStateResolver resolver = new RunTraceStateResolver(null);

  @Test
  void doesNotTraceWithoutAgentInstanceOrForTheTaskFlavor() {
    assertThat(resolver.resolve(started(AgentFlavor.SUB_PROCESS, null, null, Map.of()))).isEmpty();
    assertThat(resolver.resolve(started(AgentFlavor.TASK, AGENT_INSTANCE_KEY, null, Map.of())))
        .isEmpty();
  }

  @Test
  void startsNewRunWithoutState() {
    final Resolution resolution = resolve(null, Map.of());

    assertThat(resolution.startsRun()).isTrue();
    assertThat(resolution.previous()).isNull();
    assertThat(resolution.sessionId()).isEqualTo(Long.toString(AGENT_INSTANCE_KEY));
  }

  @Test
  void continuesOpenRunOfTheSameIteration() {
    final RunTraceState state = state(Status.OPEN, 1, PROCESS_INSTANCE_KEY);

    final Resolution resolution = resolve(1, state.toMap());

    assertThat(resolution.continuesRun()).isTrue();
    assertThat(resolution.previous()).isEqualTo(state);
  }

  @Test
  void toolDiscoveryStateContinuesIntoTheFirstTurn() {
    final Resolution resolution =
        resolve(null, state(Status.OPEN, 0, PROCESS_INSTANCE_KEY).toMap());

    assertThat(resolution.continuesRun()).isTrue();
  }

  @Test
  void closedRunStartsNewRunLinkedToThePreviousAnchor() {
    final RunTraceState state = state(Status.CLOSED, 2, PROCESS_INSTANCE_KEY);

    final Resolution resolution = resolve(2, state.toMap());

    assertThat(resolution.startsRun()).isTrue();
    assertThat(resolution.previous()).isEqualTo(state);
  }

  @Test
  void staleStateStartsNewRun() {
    final RunTraceState otherIteration = state(Status.OPEN, 1, PROCESS_INSTANCE_KEY);
    final RunTraceState otherProcess = state(Status.OPEN, 1, PROCESS_INSTANCE_KEY + 1);

    assertThat(resolve(3, otherIteration.toMap()).startsRun()).isTrue();
    assertThat(resolve(1, otherProcess.toMap()).startsRun()).isTrue();
    assertThat(resolve(3, otherIteration.toMap()).previous()).isEqualTo(otherIteration);
  }

  @Test
  void malformedStateIsTreatedAsNoState() {
    final Resolution resolution =
        resolve(1, Map.of("version", 1, "traceId", "nope", "status", "OPEN"));

    assertThat(resolution.startsRun()).isTrue();
    assertThat(resolution.previous()).isNull();
    assertThat(RunTraceState.fromMap(Map.of("version", 2))).isEmpty();
  }

  @Test
  void stateRoundTripsThroughItsMapForm() {
    final RunTraceState state = state(Status.OPEN, 4, PROCESS_INSTANCE_KEY);

    assertThat(RunTraceState.fromMap(state.toMap())).contains(state);
  }

  @Test
  void prefixesTheSessionWithTheClusterId() {
    final var prefixed = new RunTraceStateResolver("prod-eu");

    final Resolution resolution =
        prefixed
            .resolve(started(AgentFlavor.SUB_PROCESS, AGENT_INSTANCE_KEY, null, Map.of()))
            .orElseThrow();

    assertThat(resolution.sessionId()).isEqualTo("prod-eu:" + AGENT_INSTANCE_KEY);
  }

  private Resolution resolve(@Nullable Integer lastIterationKey, Map<String, Object> state) {
    return resolver
        .resolve(started(AgentFlavor.SUB_PROCESS, AGENT_INSTANCE_KEY, lastIterationKey, state))
        .orElseThrow();
  }

  private static RunTraceState state(Status status, int iterationKey, long processInstanceKey) {
    return RunTraceState.of(TRACE_ID, SPAN_ID, true, status, iterationKey, processInstanceKey);
  }

  private static JobStarted started(
      AgentFlavor flavor,
      @Nullable Long agentInstanceKey,
      @Nullable Integer lastIterationKey,
      Map<String, Object> previousState) {
    return new JobStarted(
        new AgentJobInfo(1, PROCESS_INSTANCE_KEY, 3, 4, "agent", "process", "<default>"),
        flavor,
        AgentJobKind.CONVERSATION,
        Instant.parse("2026-10-19T10:00:00Z"),
        agentInstanceKey,
        lastIterationKey,
        previousState);
  }
}
