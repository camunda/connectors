/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.openai.family;

import com.openai.client.OpenAIClient;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelRequest;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelResult;
import io.camunda.connector.agenticai.aiagent.framework.openai.OpenAiModelCapabilities;

/**
 * Performs one streaming vendor call for a specific OpenAI API family (Responses or Chat
 * Completions) and translates the result to the domain {@link ChatModelResult}. Kept as its own
 * seam so {@code OpenAiChatModelApi} stays family-agnostic and dispatches to whichever family the
 * resolved model configuration selects.
 */
public interface OpenAiApiFamilyStrategy {

  ChatModelResult call(
      OpenAIClient client,
      ChatModelRequest request,
      OpenAiModelCapabilities capabilities,
      boolean modelMatched);
}
