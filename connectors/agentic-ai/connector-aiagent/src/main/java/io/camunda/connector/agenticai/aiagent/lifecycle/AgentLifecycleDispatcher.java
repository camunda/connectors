/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.lifecycle;

import io.camunda.connector.agenticai.aiagent.api.lifecycle.AgentFlavor;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.AgentJobInfo;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.AgentJobKind;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.AgentJobObserver;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.AgentLifecycleListener;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.JobStarted;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.api.outbound.JobContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Starts the lifecycle of a job towards the registered {@link AgentLifecycleListener}s. */
public final class AgentLifecycleDispatcher {

  /** Key prefix of the listener state entries in the agent context properties. */
  public static final String STATE_KEY_PREFIX = "lifecycle:";

  private static final Logger LOGGER = LoggerFactory.getLogger(AgentLifecycleDispatcher.class);

  private final List<AgentLifecycleListener> listeners;
  private final int maxStateSize;

  public AgentLifecycleDispatcher(List<AgentLifecycleListener> listeners, int maxStateSize) {
    this.listeners = List.copyOf(listeners);
    this.maxStateSize = maxStateSize;
  }

  /**
   * Announces a job to every listener and returns the object that carries the later callbacks of
   * the job. Returns {@link AgentJobLifecycle#NOOP} when no listener is registered.
   */
  public AgentJobLifecycle startJob(
      AgentFlavor flavor,
      AgentJobKind kind,
      JobContext jobContext,
      AgentContext agentContext,
      Instant startedAt) {
    if (listeners.isEmpty()) {
      return AgentJobLifecycle.NOOP;
    }

    final var job = jobInfo(jobContext);
    final var metadata = agentContext.metadata();
    final var agentInstanceKey = metadata != null ? metadata.agentInstanceKey() : null;
    final var lastIterationKey = metadata != null ? metadata.lastIterationKey() : null;

    final var entries = new ArrayList<AgentJobLifecycle.Entry>(listeners.size());
    for (final var listener : listeners) {
      AgentJobObserver observer = AgentJobObserver.NOOP;
      try {
        observer =
            listener.onJobStarted(
                new JobStarted(
                    job,
                    flavor,
                    kind,
                    startedAt,
                    agentInstanceKey,
                    lastIterationKey,
                    previousState(agentContext, listener.id())));
      } catch (RuntimeException e) {
        LOGGER.warn("Agent lifecycle listener '{}' failed on job start", listener.id(), e);
      }
      entries.add(new AgentJobLifecycle.Entry(listener.id(), observer));
    }
    return new AgentJobLifecycle(entries, maxStateSize);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> previousState(AgentContext agentContext, String listenerId) {
    final var value = agentContext.properties().get(STATE_KEY_PREFIX + listenerId);
    if (value instanceof Map<?, ?> map) {
      return new LinkedHashMap<>((Map<String, Object>) map);
    }
    return Map.of();
  }

  private static AgentJobInfo jobInfo(JobContext jobContext) {
    return new AgentJobInfo(
        jobContext.getJobKey(),
        jobContext.getProcessInstanceKey(),
        jobContext.getProcessDefinitionKey(),
        jobContext.getElementInstanceKey(),
        jobContext.getElementId(),
        jobContext.getBpmnProcessId(),
        jobContext.getTenantId());
  }
}
