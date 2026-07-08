/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.api;

import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilities;

/**
 * Per-provider chat model. A single {@link #call(ChatModelRequest)} invocation performs exactly one
 * round-trip against the provider; implementations must not run their own internal (vendor)
 * tool-calling auto-loop.
 */
public interface ChatModelApi {

  ChatModelResult call(ChatModelRequest request);

  /**
   * The capability profile of this chat model, used by later chunks to drive runtime decisions like
   * tool-result strategy selection and reasoning negotiation. Not yet consumed by the request
   * handler.
   */
  ModelCapabilities capabilities();
}
