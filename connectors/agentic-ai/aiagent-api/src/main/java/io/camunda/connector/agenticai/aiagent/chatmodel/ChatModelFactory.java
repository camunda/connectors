/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel;

/**
 * Builds a {@link ChatModel} for the {@link ChatModelConfiguration}s it supports. Each provider
 * contributes one factory; the registry routes a configuration to the single factory whose {@link
 * #supports} returns true.
 */
public interface ChatModelFactory {

  /** Whether this factory can serve the given configuration. */
  boolean supports(ChatModelConfiguration configuration);

  /** Builds the chat model. Only called when {@link #supports(ChatModelConfiguration)} is true. */
  ChatModel create(ChatModelConfiguration configuration);
}
