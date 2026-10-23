/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.tool;

import io.camunda.connector.agenticai.aiagent.model.document.DocumentId;
import io.camunda.connector.agenticai.common.annotation.Experimental;
import java.util.Map;

/**
 * A call of a runtime tool by the model. The framework implements this interface and may add
 * methods in a later release. {@link Experimental}, see {@link RuntimeTool}.
 *
 * <p>The arguments are the values the model sent, except that a parameter that carries a document
 * (see {@link RuntimeToolSchemas}) holds the {@link DocumentId} of the document instead of the
 * {@code <doc id="..." />} tag the model wrote.
 */
@Experimental
public interface RuntimeToolCall {

  String id();

  String name();

  /**
   * The arguments, with an unmodifiable top level. The values are JSON values: a {@link String}, a
   * {@link Number}, a {@link Boolean}, a {@link java.util.List}, a {@link Map} or {@code null}, and
   * a {@link DocumentId} for a parameter that carries a document.
   */
  Map<String, Object> arguments();

  /**
   * Binds the {@link #arguments() arguments} to {@code type}, a record or a bean with a property
   * per argument. A {@link DocumentId} property receives the document of a document parameter, a
   * {@link String} property the text of the id. Properties without an argument are {@code null},
   * arguments without a property are ignored.
   *
   * @throws RuntimeToolException if the arguments do not fit {@code type}, which gives the call an
   *     error result
   */
  <A> A argumentsAs(Class<A> type);
}
