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
 */
@Experimental
public interface RuntimeToolCall {

  String id();

  String name();

  /**
   * The arguments, with an unmodifiable top level. The values are JSON values: a {@link String}, a
   * {@link Number}, a {@link Boolean}, a {@link java.util.List}, a {@link Map} or {@code null}. A
   * document parameter holds the {@link AgentDocumentId} of the document.
   */
  Map<String, Object> arguments();

  /**
   * Binds the {@link #arguments() arguments} to {@code type}, a record or a bean with a property
   * per argument. Bind a document parameter to an {@link AgentDocumentId} property. Properties
   * without an argument are {@code null}, arguments without a property are ignored.
   *
   * @throws RuntimeToolException if the arguments do not fit {@code type}, which gives the call an
   *     error result
   */
  <A> A argumentsAs(Class<A> type);
}
