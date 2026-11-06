/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.tool;

import io.camunda.connector.agenticai.aiagent.model.document.AgentDocument;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.message.content.AgentDocumentContent;
import io.camunda.connector.agenticai.common.annotation.Experimental;
import io.camunda.connector.api.document.Document;
import java.util.List;
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

  /** All documents the agent knows, in the order they entered the conversation. */
  List<AgentDocument> all();

  /**
   * The reference block of a known document, to put into the {@link RuntimeToolResult#success(List)
   * content} of a result. The model reads it as the handle of the document, which it can pass to a
   * tool as a document parameter. The framework fills in the file name, content type and size of
   * the handle.
   *
   * @throws IllegalArgumentException if the agent does not know a document with this id
   */
  AgentDocumentContent reference(AgentDocumentId id);

  /**
   * The live document of a known document, to read its metadata and content. Reading the content of
   * an external document downloads it.
   *
   * @throws IllegalArgumentException if the agent does not know a document with this id
   * @throws IllegalStateException if the document cannot be restored from the state of the agent
   */
  Document document(AgentDocumentId id);

  /**
   * Whether the model reads the content of the document in its conversation as of the start of the
   * batch of calls: a message within the context window holds it. The agent does not add the
   * content of such a document a second time (see {@link
   * RuntimeToolResult.Success#withDocumentContent}). It does not change while the batch runs. The
   * context passed to {@link RuntimeTool#isEnabled} has no batch, so it is {@code false} there.
   */
  boolean hasContentInConversation(AgentDocumentId id);
}
