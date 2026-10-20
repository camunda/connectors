/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.otel;

import io.camunda.connector.agenticai.aiagent.api.lifecycle.AgentFlavor;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.JobStarted;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Decides from the job-started event whether a job is traced and whether it continues the run of
 * the previous persisting job or starts a new one.
 */
public class RunTraceStateResolver {

  private final @Nullable String clusterId;

  public RunTraceStateResolver(@Nullable String clusterId) {
    this.clusterId = clusterId == null || clusterId.isBlank() ? null : clusterId;
  }

  /**
   * The outcome for a traced job.
   *
   * @param sessionId the session identifier of the conversation, the same for every job and run
   * @param continuesRun whether the job continues the run described by {@code previous}
   * @param previous the readable persisted state: the run to continue when {@code continuesRun},
   *     the run that ended otherwise. Absent when no readable state exists, which makes the job
   *     start the first run.
   */
  public record Resolution(
      String sessionId, boolean continuesRun, @Nullable RunTraceState previous) {

    /** Whether this job starts a new run and therefore owns the anchor. */
    public boolean startsRun() {
      return !continuesRun;
    }
  }

  /** Resolves the job, or returns empty when it is not traced. */
  public Optional<Resolution> resolve(JobStarted event) {
    final Long agentInstanceKey = event.agentInstanceKey();
    if (agentInstanceKey == null || event.flavor() != AgentFlavor.SUB_PROCESS) {
      return Optional.empty();
    }
    final String sessionId =
        clusterId != null ? clusterId + ":" + agentInstanceKey : Long.toString(agentInstanceKey);

    final RunTraceState previous = RunTraceState.fromMap(event.previousState()).orElse(null);
    final boolean continues =
        previous != null
            && previous.status() == RunTraceState.Status.OPEN
            && previous.iterationKey() == iterationKeyOf(event);
    return Optional.of(new Resolution(sessionId, continues, previous));
  }

  /** A tool discovery dispatch keeps the persisted iteration key; no persisted turn counts as 0. */
  private static int iterationKeyOf(JobStarted event) {
    final Integer lastIterationKey = event.lastIterationKey();
    return lastIterationKey != null ? lastIterationKey : 0;
  }
}
