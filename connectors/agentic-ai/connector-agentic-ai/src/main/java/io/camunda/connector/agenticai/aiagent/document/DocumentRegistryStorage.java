/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;

/**
 * Where the {@link DocumentRegistry} of an agent lives. The registry is currently carried by the
 * {@link AgentContext}; the interface keeps the callers independent of that so the registry can
 * move into the conversation stores.
 */
public interface DocumentRegistryStorage {

  /** Returns the registry persisted for the given agent, empty if none was persisted yet. */
  DocumentRegistry load(AgentContext agentContext);

  /** Returns the agent context carrying the given registry. */
  AgentContext store(AgentContext agentContext, DocumentRegistry registry);
}
