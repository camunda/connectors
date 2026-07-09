/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.anthropic;

import com.anthropic.client.AnthropicClient;

/** Builds vendor {@link AnthropicClient} instances used by {@link AnthropicChatModelApi}. */
public interface AnthropicClientFactory {

  /** Builds a fresh {@link AnthropicClient}; the caller owns its lifecycle. */
  AnthropicClient create();
}
