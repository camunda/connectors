/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.tool;

import io.camunda.connector.agenticai.aiagent.model.document.DocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentSource;
import io.camunda.connector.agenticai.common.annotation.Experimental;
import io.camunda.connector.api.document.Document;

/**
 * A read-only view of a document the agent knows. The framework implements this interface: a method
 * may be added in a later release.
 *
 * <p>{@link Experimental}, see {@link RuntimeTool}.
 */
@Experimental
public interface AgentDocument {

  /** The stable id of the document, the one in its tag. */
  DocumentId id();

  /** Where the document entered the conversation. */
  DocumentSource source();

  /**
   * The document itself, to read its metadata and content. Reading the content of an external
   * document downloads it.
   */
  Document document();
}
