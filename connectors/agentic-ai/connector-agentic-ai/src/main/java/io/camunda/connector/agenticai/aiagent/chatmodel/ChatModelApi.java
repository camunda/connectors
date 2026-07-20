/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel;

import io.camunda.connector.agenticai.aiagent.capabilities.ModelCapabilities;

/**
 * Per-provider chat model. One instance serves a single agent request across all of its
 * continuation rounds and must be closed once that request is done; a single {@link
 * #call(ChatModelRequest)} invocation performs exactly one round-trip against the provider.
 */
public interface ChatModelApi extends AutoCloseable {

  ChatModelResult call(ChatModelRequest request);

  /**
   * The capability profile of this chat model, used to drive runtime decisions like tool-result
   * strategy selection and reasoning negotiation.
   */
  ModelCapabilities capabilities();

  @Override
  void close();
}
