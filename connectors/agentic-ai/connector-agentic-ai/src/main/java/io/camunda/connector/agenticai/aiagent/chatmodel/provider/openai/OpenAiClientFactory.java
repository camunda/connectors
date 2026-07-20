/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai;

import com.openai.client.OpenAIClient;

/** Builds vendor {@link OpenAIClient} instances used by the native OpenAI chat model API. */
public interface OpenAiClientFactory {

  /** Builds a fresh {@link OpenAIClient}; the caller owns its lifecycle. */
  OpenAIClient create();
}
