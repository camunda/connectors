/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai;

import com.openai.client.OpenAIClient;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatRequest;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.OpenAiApiFamilyStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenAI {@link ChatModel}: delegates the actual streaming vendor call to the {@link
 * OpenAiApiFamilyStrategy} selected for the configured {@code configuration.api()} family
 * (Responses or Chat Completions) by the factory, then returns the result the strategy already
 * translated to the domain {@link ChatResult}.
 *
 * <p>The {@link OpenAIClient} is built once by the factory and owned for the lifetime of this
 * instance (one agent request, across all continuation rounds); {@link #close()} closes it once.
 * {@link OpenAIClient#close()} is a plain, unchecked {@code void} method (the vendor SDK's client
 * interface does not implement {@link AutoCloseable}), so it is closed explicitly and guarded.
 */
public class OpenAiChatModel implements ChatModel {

  private static final Logger LOG = LoggerFactory.getLogger(OpenAiChatModel.class);

  private final OpenAIClient client;
  private final OpenAiConfiguration configuration;
  private final OpenAiApiFamilyStrategy strategy;

  public OpenAiChatModel(
      OpenAIClient client, OpenAiConfiguration configuration, OpenAiApiFamilyStrategy strategy) {
    this.client = client;
    this.configuration = configuration;
    this.strategy = strategy;
  }

  @Override
  public ChatResult execute(ChatRequest request) {
    return strategy.call(client, configuration, request);
  }

  @Override
  public void close() {
    try {
      client.close();
    } catch (Exception e) {
      LOG.error("Failed to close OpenAIClient", e);
    }
  }
}
