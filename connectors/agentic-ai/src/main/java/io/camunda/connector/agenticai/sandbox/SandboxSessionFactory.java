/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.sandbox.spi.SandboxSession;
import java.util.Optional;

/**
 * Factory for opening (or reconnecting to) a sandbox session for the current invocation. Returns
 * empty when no sandbox is configured.
 */
public interface SandboxSessionFactory {
  Optional<SandboxSession> openSession(AgentExecutionContext ctx, AgentContext agentContext);
}
