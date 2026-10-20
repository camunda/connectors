/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.lifecycle;

import io.camunda.connector.agenticai.aiagent.api.Experimental;
import io.camunda.connector.api.outbound.JobCompletionFailure;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Receives the callbacks of one job. Created by {@link AgentLifecycleListener#onJobStarted}; all
 * callbacks have no-op defaults.
 *
 * <p>Three kinds of callbacks exist, each with its own contract:
 *
 * <ul>
 *   <li><b>Notifications</b> ({@code onJobDeferred}, {@code onTurnStarted}, {@code onJobFailed},
 *       {@code onJobCompleted}, {@code onJobCompletionFailed}) are failure-isolated: an exception
 *       is logged and ignored, callbacks of several listeners run in registration order on the job
 *       thread, and a notification cannot change the job. The log names the listener, the callback,
 *       and the exception class, never the event content.
 *   <li><b>State contributions</b> ({@code onToolDiscoveryDispatched}, {@code onTurnCompleted})
 *       return the listener's state, which the framework stores in the agent context under {@code
 *       lifecycle:<listener id>} and hands back in {@link JobStarted#previousState()} of the next
 *       job that reads it. A job's state is written only on the two paths that persist the agent
 *       context, and is lost together with a job that fails or is superseded. A listener must treat
 *       missing state as "start over". The state is a map of plain JSON values only: maps with
 *       string keys, lists, strings, booleans, finite numbers, and null. Its JSON serialization has
 *       a size limit. An invalid, oversized, or failing contribution is dropped and the stored
 *       state of the listener stays as it was; the job is not affected.
 *   <li><b>Model rounds</b> ({@code onModelRoundStarted}, then {@code onModelRoundCompleted} or
 *       {@code onModelRoundFailed}) are paired by the framework on the job thread: every started
 *       round is closed, even when a listener throws. A listener cannot skip or alter the call.
 * </ul>
 *
 * <p>Events carry complete data, including prompts, tool arguments, and tool results. Applying
 * capture limits or redaction is the responsibility of the listener.
 */
@Experimental
public interface AgentJobObserver {

  /** Observer that ignores every callback. */
  AgentJobObserver NOOP = new AgentJobObserver() {};

  /** The job completes without a turn because tool results are still missing. */
  default void onJobDeferred(JobDeferred event) {}

  /**
   * The job dispatches tool discovery and persists the agent context.
   *
   * @return state to store in the agent context, or null to leave the stored state unchanged
   */
  default @Nullable Map<String, Object> onToolDiscoveryDispatched(ToolDiscoveryDispatched event) {
    return null;
  }

  /** A turn starts. */
  default void onTurnStarted(TurnStarted event) {}

  default void onModelRoundStarted(ModelRoundStarted event) {}

  default void onModelRoundCompleted(ModelRoundCompleted event) {}

  default void onModelRoundFailed(ModelRoundFailed event) {}

  /**
   * A turn is complete and the agent context is about to be persisted.
   *
   * @return state to store in the agent context, or null to leave the stored state unchanged
   */
  default @Nullable Map<String, Object> onTurnCompleted(TurnCompleted event) {
    return null;
  }

  /** The agent work of the job threw. */
  default void onJobFailed(JobFailed event) {}

  /** The engine accepted the job completion. Runs after the complete command succeeded. */
  default void onJobCompleted() {}

  /** The job completion did not succeed, for example because the job was superseded. */
  default void onJobCompletionFailed(JobCompletionFailure failure) {}
}
