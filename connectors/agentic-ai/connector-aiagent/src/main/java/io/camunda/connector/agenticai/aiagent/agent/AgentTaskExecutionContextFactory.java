/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.request.AgentTaskV1Request;
import io.camunda.connector.agenticai.aiagent.request.AgentTaskV2Request;
import io.camunda.connector.api.outbound.OutboundConnectorContext;

/**
 * Builds the AI Agent Task execution context of one job from the connector context of the job and
 * the bound request. The connector context gives the job context and the document factory.
 */
public interface AgentTaskExecutionContextFactory {

  /**
   * Returns the execution context for a v1 request. Its provider configuration is the v1
   * configuration of the request or its v2 equivalent, see {@link
   * AgentExecutionContextFactory#create}.
   */
  AgentTaskExecutionContext taskContext(
      OutboundConnectorContext context, AgentTaskV1Request request);

  /** Returns the execution context for a v2 request, with its provider configuration. */
  AgentTaskExecutionContext taskContext(
      OutboundConnectorContext context, AgentTaskV2Request request);
}
