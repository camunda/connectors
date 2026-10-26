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
   * Whether the model reads the content of the document in its conversation as of the start of the
   * batch of calls this document was read in: a message within the context window holds it. The
   * agent does not add the content of such a document a second time (see {@link
   * RuntimeToolResult.Success#withDocumentContent}). It does not change while the batch runs. The
   * context passed to {@link RuntimeTool#isEnabled} has no batch, so it is {@code false} there.
   */
  boolean contentInConversation();

  /**
   * The document itself, to read its metadata and content. Reading the content of an external
   * document downloads it.
   *
   * @throws IllegalStateException if the document cannot be restored from the state of the agent
   */
  Document document();
}
