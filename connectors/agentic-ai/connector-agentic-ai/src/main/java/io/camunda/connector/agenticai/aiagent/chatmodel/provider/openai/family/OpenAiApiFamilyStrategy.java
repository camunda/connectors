/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family;

import com.openai.client.OpenAIClient;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatRequest;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration;

/**
 * Performs one streaming vendor call for a specific OpenAI API family (Responses or Chat
 * Completions) and translates the result to the domain {@link ChatResult}. Kept as its own seam so
 * {@code OpenAiChatModel} stays family-agnostic and dispatches to whichever family the resolved
 * model configuration selects. A family strategy instance is shared across every OpenAI model
 * configuration selecting that family, so the resolved {@link OpenAiChatModelConfiguration} is
 * passed per call rather than fixed at construction.
 */
public interface OpenAiApiFamilyStrategy {

  ChatResult call(
      OpenAIClient client, OpenAiChatModelConfiguration configuration, ChatRequest request);
}
