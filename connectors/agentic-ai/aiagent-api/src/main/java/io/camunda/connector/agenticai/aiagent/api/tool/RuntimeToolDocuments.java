/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.tool;

import io.camunda.connector.agenticai.aiagent.model.document.DocumentId;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentReferenceContent;
import io.camunda.connector.agenticai.common.annotation.Experimental;
import java.util.List;
import java.util.Optional;

/**
 * The documents the agent knows at the start of a batch of runtime tool calls: the documents of the
 * user prompts, events and tool results in the context window the model was called with when it
 * asked for the calls. A document that left the window is not part of it, nor are the documents the
 * calls of the batch return. The framework implements this interface: a method may be added in a
 * later release.
 *
 * <p>{@link Experimental}, see {@link RuntimeTool}.
 */
@Experimental
public interface RuntimeToolDocuments {

  /** The document with the given id, or empty if the agent does not know it. */
  Optional<AgentDocument> find(DocumentId id);

  /** All documents the agent knows, in the order they entered the conversation. */
  List<AgentDocument> all();

  /**
   * The reference block of a known document, to put into the {@link RuntimeToolResult#success(List)
   * content} of a result. The model reads it as the {@code <doc id="..." />} tag of the document,
   * which it can pass to a tool as a document parameter. The framework fills in the attributes of
   * the tag.
   *
   * @throws IllegalArgumentException if the agent does not know a document with this id
   */
  DocumentReferenceContent reference(DocumentId id);
}
