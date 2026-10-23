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
 * A call of a runtime tool by the model. The framework implements this interface: a method may be
 * added in a later release.
 *
 * <p>{@link Experimental}, see {@link RuntimeTool}.
 *
 * <p>The arguments are the values the model sent, with one difference: a parameter that carries a
 * document, which the schema marks as such (see {@link RuntimeToolSchemas}), holds the {@link
 * DocumentId} of the document instead of the {@code <doc id="..." />} tag the model wrote. The
 * framework has checked that the document is known before it hands the call to the tool.
 */
@Experimental
public interface RuntimeToolCall {

  /** The id of the tool call, assigned by the model provider. */
  String id();

  /** The name of the called tool. */
  String name();

  /**
   * The arguments in the order the model sent them, unmodifiable. The values are JSON values: a
   * {@link String}, a {@link Number}, a {@link Boolean}, a {@link java.util.List}, a {@link Map} or
   * {@code null}, and a {@link DocumentId} for a parameter that carries a document.
   */
  Map<String, Object> arguments();

  /**
   * Binds the {@link #arguments() arguments} to {@code type}, a record or a bean with a property
   * per argument. A {@link DocumentId} property receives the document of a document parameter. A
   * property typed {@link String} receives the text of the id.
   *
   * <p>Properties of {@code type} without an argument are {@code null}, and arguments without a
   * property are ignored.
   *
   * @throws RuntimeToolException if the arguments do not fit {@code type}; the framework gives the
   *     call an error result that carries the reason
   */
  <A> A argumentsAs(Class<A> type);
}
