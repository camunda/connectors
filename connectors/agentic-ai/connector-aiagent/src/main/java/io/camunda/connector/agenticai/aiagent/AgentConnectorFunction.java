/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent;

import io.camunda.connector.agenticai.aiagent.agent.AgentJobCompletionListener;
import io.camunda.connector.api.outbound.ConnectorResponse;
import io.camunda.connector.api.outbound.JobCompletionFailure;
import io.camunda.connector.api.outbound.JobCompletionListener;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;

/**
 * Common contract for AI agent connector functions: an {@link OutboundConnectorFunction} that also
 * acts as the SDK-level {@link JobCompletionListener}. The default callback implementations
 * delegate to an {@link AgentJobCompletionListener} carried by the response, so per-execution state
 * (conversation store, agent context, ...) captured during {@code execute()} stays in scope when
 * the Zeebe completion command resolves.
 */
public interface AgentConnectorFunction extends OutboundConnectorFunction, JobCompletionListener {

  @Override
  ConnectorResponse execute(OutboundConnectorContext context) throws Exception;

  @Override
  default void onJobCompleted(OutboundConnectorContext context, ConnectorResponse response) {
    if (response instanceof AgentJobCompletionListener listener) {
      listener.onJobCompleted();
    }
  }

  @Override
  default void onJobCompletionFailed(
      OutboundConnectorContext context, ConnectorResponse response, JobCompletionFailure failure) {
    if (response instanceof AgentJobCompletionListener listener) {
      listener.onJobCompletionFailed(failure);
    }
  }
}
