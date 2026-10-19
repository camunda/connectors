/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.otel;

import io.camunda.connector.agenticai.aiagent.api.lifecycle.AgentLifecycleListener;
import io.camunda.connector.agenticai.otel.configuration.AgentTracingProperties;
import io.opentelemetry.api.OpenTelemetry;

/** Turns the lifecycle events of the AI Agent into OpenTelemetry GenAI spans. */
public class AgentTracingListener implements AgentLifecycleListener {

  /** Namespace of the run tracing state in the agent context. Must not change. */
  public static final String ID = "otel-tracing";

  private final OpenTelemetry openTelemetry;
  private final AgentTracingProperties properties;

  public AgentTracingListener(OpenTelemetry openTelemetry, AgentTracingProperties properties) {
    this.openTelemetry = openTelemetry;
    this.properties = properties;
  }

  @Override
  public String id() {
    return ID;
  }
}
