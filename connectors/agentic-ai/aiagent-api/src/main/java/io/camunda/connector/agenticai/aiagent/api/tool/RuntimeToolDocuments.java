/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.tool;

import io.camunda.connector.agenticai.aiagent.model.document.AgentDocument;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.common.annotation.Experimental;
import java.util.Optional;

/**
 * The documents the agent knows at the start of a batch of runtime tool calls: the documents of the
 * user prompt, of events and of tool results of this agent instance. The documents the calls of the
 * batch return are not part of it. The framework implements this interface: a method may be added
 * in a later release.
 *
 * <p>{@link Experimental}, see {@link RuntimeTool}.
 */
@Experimental
public interface RuntimeToolDocuments {

  /** The document with the given id, or empty if the agent does not know it. */
  Optional<AgentDocument> find(AgentDocumentId id);
}
