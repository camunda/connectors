/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel;

/**
 * Per-provider chat model. One instance serves a single agent request across all of its
 * continuation rounds and must be closed once that request is done; a single {@link
 * #execute(ChatRequest)} invocation performs exactly one round-trip against the provider.
 */
public interface ChatModel extends AutoCloseable {

  ChatResult execute(ChatRequest request);

  @Override
  void close();
}
