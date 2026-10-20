/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.otel;

import io.camunda.connector.agenticai.aiagent.api.lifecycle.AgentJobObserver;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.AgentLifecycleListener;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.JobStarted;
import io.camunda.connector.agenticai.otel.configuration.AgentTracingProperties;
import io.opentelemetry.api.OpenTelemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns the lifecycle events of the AI Agent into OpenTelemetry GenAI spans. Only jobs of the AI
 * Agent Sub-process with an agent instance are traced. Telemetry is best effort: no failure of this
 * listener reaches the job.
 */
public class AgentTracingListener implements AgentLifecycleListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(AgentTracingListener.class);

  /** Namespace of the run tracing state in the agent context. Must not change. */
  public static final String ID = "otel-tracing";

  private final AgentTracer tracer;
  private final RunTraceStateResolver resolver;
  private final boolean captureContent;

  public AgentTracingListener(OpenTelemetry openTelemetry, AgentTracingProperties properties) {
    this(new AgentTracer(openTelemetry), properties);
  }

  AgentTracingListener(AgentTracer tracer, AgentTracingProperties properties) {
    this.tracer = tracer;
    this.resolver = new RunTraceStateResolver(properties.clusterId());
    this.captureContent = properties.captureContent();
  }

  @Override
  public String id() {
    return ID;
  }

  @Override
  public AgentJobObserver onJobStarted(JobStarted event) {
    try {
      return resolver
          .resolve(event)
          // the capture policy is decided once per job
          .<AgentJobObserver>map(
              resolution -> new AgentTracingJobObserver(tracer, event, resolution, captureContent))
          .orElse(AgentJobObserver.NOOP);
    } catch (Throwable t) {
      LOGGER.debug("Agent tracing could not start the job observer", t);
      return AgentJobObserver.NOOP;
    }
  }
}
