/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.request.AgentSubProcessV1Request;
import io.camunda.connector.agenticai.aiagent.request.AgentSubProcessV2Request;
import io.camunda.connector.api.outbound.OutboundConnectorContext;

/**
 * Builds the AI Agent Sub-process execution context of one job from the connector context of the
 * job and the bound request. The connector context gives the job context and the document factory.
 *
 * <p>The auto-configuration registers a default bean that implements this interface and {@link
 * AgentTaskExecutionContextFactory}. A bean of either interface replaces the default, so a
 * replacement must provide both interfaces.
 */
public interface AgentSubProcessExecutionContextFactory {

  /**
   * Returns the execution context for a v1 request. Its chat model configuration is the v1 provider
   * configuration of the request, or its v2 equivalent when the connector rewrites v1 provider
   * configurations to v2.
   */
  AgentSubProcessExecutionContext subProcessContext(
      OutboundConnectorContext context, AgentSubProcessV1Request request);

  /** Returns the execution context for a v2 request, with its provider configuration. */
  AgentSubProcessExecutionContext subProcessContext(
      OutboundConnectorContext context, AgentSubProcessV2Request request);
}
