/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.azurefoundry.langchain4j;

import com.anthropic.client.AnthropicClient;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureFoundryProviderConfiguration.AzureAiFoundryModel.AnthropicModel;

/**
 * Stub class; the real ChatModel adapter implementation lives in a follow-up task.
 *
 * <p>This stub exists so the AnthropicOnFoundryClientFactory can compile and be tested in
 * isolation. Task 7 replaces this file with a full implementation that implements {@code
 * dev.langchain4j.model.chat.ChatModel}.
 */
public class AnthropicOnFoundryChatModel {

  private final AnthropicClient client;
  private final AnthropicModel modelConfig;

  public AnthropicOnFoundryChatModel(AnthropicClient client, AnthropicModel modelConfig) {
    this.client = client;
    this.modelConfig = modelConfig;
  }

  public AnthropicModel modelConfig() {
    return modelConfig;
  }
}
