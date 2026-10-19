/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.lifecycle;

import io.camunda.connector.agenticai.aiagent.api.lifecycle.AgentFlavor;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.AgentJobKind;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.api.outbound.JobContext;
import java.time.Instant;

/** Starts the lifecycle of a job towards the registered {@code AgentLifecycleListener}s. */
public interface AgentLifecycleDispatcher {

  /**
   * Announces a job to every listener and returns the object that carries the later callbacks of
   * the job. Returns {@link AgentJobLifecycle#NOOP} when no listener is registered.
   */
  AgentJobLifecycle startJob(
      AgentFlavor flavor,
      AgentJobKind kind,
      JobContext jobContext,
      AgentContext agentContext,
      Instant startedAt);
}
