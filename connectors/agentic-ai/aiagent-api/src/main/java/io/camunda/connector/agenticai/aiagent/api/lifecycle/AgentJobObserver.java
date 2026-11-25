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
 *   <li><b>Notifications</b> ({@code onJobDeferred}, {@code onJobFailed}, {@code onJobCompleted},
 *       {@code onJobCompletionFailed}) are failure-isolated: an exception is logged and ignored,
 *       callbacks of several listeners run in registration order on the job thread, and a
 *       notification cannot change the job. The log names the listener, the callback, and the
 *       exception class, never the event content.
 *   <li><b>State contributions</b> ({@code onToolDiscoveryDispatched}, {@code
 *       onAgentResponseCreated}) return the listener's state, which the framework stores in the
 *       agent context under {@code lifecycle:<listener id>} and hands back in {@link
 *       JobStarted#previousState()} of the next job that reads it. A job's state is written only on
 *       the two paths that persist the agent context, and is lost together with a job that fails or
 *       is superseded. A listener must treat missing state as "start over". The state is a map of
 *       plain JSON values only: maps with string keys, lists, strings, booleans, finite numbers,
 *       and null. Its JSON serialization has a size limit. An invalid, oversized, or failing
 *       contribution is dropped and the stored state of the listener stays as it was; the job is
 *       not affected.
 *   <li><b>Turns</b> ({@code onTurnStarted}, then {@code onTurnCompleted} or {@code onTurnFailed})
 *       are notifications too, paired by the framework on the job thread: every started turn is
 *       closed, even when a listener throws. A turn is one call to the chat model, and a job runs
 *       several turns when the provider pauses a response or when the model calls only tools that
 *       the agent answers itself. A listener cannot skip or alter the call.
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
   * The job dispatches tool discovery and persists the agent context. Runs after the lease-fenced
   * agent instance write succeeded, so only a job that holds the lease reports it.
   *
   * @return state to store in the agent context, or null to leave the stored state unchanged
   */
  default @Nullable Map<String, Object> onToolDiscoveryDispatched(ToolDiscoveryDispatched event) {
    return null;
  }

  /**
   * A turn starts. Runs after the lease-fenced agent instance write of the first turn of the job
   * succeeded, so only a job that holds the lease reports it. A job that fails before that write,
   * for example because it reached the model call limit or was superseded, reports {@link
   * #onJobFailed} without a turn start.
   */
  default void onTurnStarted(TurnStarted event) {}

  /** The chat model call of the turn returned. */
  default void onTurnCompleted(TurnCompleted event) {}

  /** The chat model call of the turn failed. */
  default void onTurnFailed(TurnFailed event) {}

  /**
   * The agent response of the job is created and the agent context is about to be persisted.
   *
   * @return state to store in the agent context, or null to leave the stored state unchanged
   */
  default @Nullable Map<String, Object> onAgentResponseCreated(AgentResponseCreated event) {
    return null;
  }

  /** The agent work of the job threw. */
  default void onJobFailed(JobFailed event) {}

  /** The engine accepted the job completion. Runs after the complete command succeeded. */
  default void onJobCompleted() {}

  /** The job completion did not succeed, for example because the job was superseded. */
  default void onJobCompletionFailed(JobCompletionFailure failure) {}
}
