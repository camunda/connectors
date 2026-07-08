/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.api;

/** Builds a {@link ChatModelApi} for the chat model configurations it supports. */
public interface ChatModelApiFactory {

  int DEFAULT_ORDER = 0;

  /** Whether this factory can serve the given configuration. */
  boolean supports(ChatModelApiConfiguration configuration);

  /**
   * Builds the chat model. Only called when {@link #supports(ChatModelApiConfiguration)} is true.
   */
  ChatModelApi create(ChatModelApiConfiguration configuration);

  /**
   * Selection precedence when multiple factories support a configuration: lowest value wins. The
   * LangChain4j bridge returns the lowest precedence so any native implementation overrides it.
   *
   * @return The order value (default: {@link #DEFAULT_ORDER})
   */
  default int getOrder() {
    return DEFAULT_ORDER;
  }
}
