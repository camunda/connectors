/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.tool;

import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.common.annotation.Experimental;
import java.util.Map;

/**
 * A call of a runtime tool by the model. The framework implements this interface and may add
 * methods in a later release. {@link Experimental}, see {@link RuntimeTool}.
 *
 * <p>The arguments are the values the model sent. For an agent instance that tracks its documents,
 * a parameter that carries a document (see {@link DocumentSchemas}) holds the {@link
 * AgentDocumentId} of the document in place of the document handle the model wrote. For an agent
 * instance that does not track its documents, every argument is the value the model sent, also a
 * document handle.
 */
@Experimental
public interface RuntimeToolCall {

  String id();

  String name();

  /**
   * The arguments, with an unmodifiable top level. The values are JSON values: a {@link String}, a
   * {@link Number}, a {@link Boolean}, a {@link java.util.List}, a {@link Map} or {@code null}, and
   * an {@link AgentDocumentId} for a parameter that carries a document. For an agent instance that
   * tracks its documents:
   *
   * <ul>
   *   <li>A document parameter holds an {@link AgentDocumentId}.
   *   <li>A union parameter (an {@code anyOf} or {@code oneOf} with {@link
   *       DocumentSchemas#document} as one alternative, directly or inside an object or array
   *       alternative) holds an {@link AgentDocumentId} for every document handle in the value the
   *       model passed, and the rest of the value as the model passed it. A string that is not a
   *       handle as JSON is text, also one that equals the id of a document.
   * </ul>
   *
   * For an agent instance that does not track its documents, every value is the value the model
   * sent, and no value is an {@link AgentDocumentId}.
   */
  Map<String, Object> arguments();

  /**
   * Binds the {@link #arguments() arguments} to {@code type}, a record or a bean with a property
   * per argument. An {@link AgentDocumentId} property receives the document of a document
   * parameter, a {@link String} property the text of the id. A property typed {@link Object}
   * receives the argument as {@link #arguments()} holds it: an {@link AgentDocumentId} for a
   * document, and the JSON value the model passed for any other value, such as a {@link String}, a
   * {@link java.util.List} or a {@link Map}. Properties without an argument are {@code null},
   * arguments without a property are ignored.
   *
   * @throws RuntimeToolException if the arguments do not fit {@code type}, which gives the call an
   *     error result
   */
  <A> A argumentsAs(Class<A> type);
}
