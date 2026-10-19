/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.lifecycle;

import io.camunda.connector.agenticai.aiagent.api.Experimental;

/**
 * Observes the execution of AI Agent jobs. Register an implementation as a Spring bean; the AI
 * Agent calls {@link #onJobStarted} for every job of both flavors and sends the returned observer
 * the callbacks of that job.
 *
 * <p>Every registered listener receives complete event data, including prompts, tool arguments, and
 * tool results, and never the lease token of the job. Installing a listener is a data-handling
 * decision of the operator.
 *
 * <p>See {@link AgentJobObserver} for the contract of each kind of callback.
 */
@Experimental
public interface AgentLifecycleListener {

  /**
   * Stable identifier of this listener. It names the listener's state in the agent context, so it
   * must not change once state has been persisted, which is why a bean name is not used.
   */
  String id();

  /**
   * A job started. Return the observer for this job, or {@link AgentJobObserver#NOOP}. A listener
   * that throws gets no observer for the job.
   */
  default AgentJobObserver onJobStarted(JobStarted event) {
    return AgentJobObserver.NOOP;
  }
}
