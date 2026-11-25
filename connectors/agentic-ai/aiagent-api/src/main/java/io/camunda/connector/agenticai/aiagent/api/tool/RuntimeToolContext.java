/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.tool;

import io.camunda.connector.agenticai.aiagent.api.Experimental;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.api.document.Document;
import java.util.Optional;

/**
 * A read-only view of the agent as of a call, safe to read from several threads. The framework
 * implements this interface and may add methods in a later release. {@link Experimental}, see
 * {@link RuntimeTool}.
 */
@Experimental
public interface RuntimeToolContext {

  /** The configuration, prompts and limits of the invocation. */
  AgentExecutionContext executionContext();

  /** The agent context at the time of the call. It does not change while the call is executed. */
  AgentContext agentContext();

  /**
   * The document with this id, such as the value of a document parameter of the call. A result can
   * hold the document.
   *
   * @return empty when the agent does not know the id
   */
  Optional<Document> document(AgentDocumentId id);
}
