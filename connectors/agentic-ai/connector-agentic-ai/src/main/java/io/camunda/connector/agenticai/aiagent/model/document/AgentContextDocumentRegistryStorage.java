/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;

/**
 * Persists the registry in {@link AgentContext#documents()}, independent of the conversation store.
 */
public class AgentContextDocumentRegistryStorage implements DocumentRegistryStorage {

  @Override
  public DocumentRegistry load(AgentContext agentContext) {
    return DocumentRegistry.of(agentContext.documents());
  }

  @Override
  public AgentContext store(AgentContext agentContext, DocumentRegistry registry) {
    return agentContext.withDocuments(registry.entries());
  }
}
