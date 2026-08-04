/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL;

import com.openai.client.OpenAIClient;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatRequest;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.OpenAiApiFamilyStrategy;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration;
import io.camunda.connector.api.error.ConnectorException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenAI {@link ChatModel}: delegates the actual streaming vendor call to the {@link
 * OpenAiApiFamilyStrategy} selected for the configured {@code configuration.openai().api()} family
 * (Responses or Chat Completions) by the factory, then returns the result the strategy already
 * translated to the domain {@link ChatResult}.
 *
 * <p>The {@link OpenAIClient} is built once by the factory and owned for the lifetime of this
 * instance (one agent request, across all continuation rounds); {@link #close()} closes it once.
 * {@link OpenAIClient#close()} is a plain, unchecked {@code void} method (the vendor SDK's client
 * interface does not implement {@link AutoCloseable}), so it is closed explicitly and guarded,
 * mirroring the Anthropic sibling's handling of {@code AnthropicClient}.
 */
public class OpenAiChatModelApi implements ChatModel {

  private static final Logger LOG = LoggerFactory.getLogger(OpenAiChatModelApi.class);

  private final OpenAIClient client;
  private final OpenAiChatModelConfiguration configuration;
  private final OpenAiApiFamilyStrategy strategy;

  public OpenAiChatModelApi(
      OpenAIClient client,
      OpenAiChatModelConfiguration configuration,
      OpenAiApiFamilyStrategy strategy) {
    this.client = client;
    this.configuration = configuration;
    this.strategy = strategy;
  }

  @Override
  public ChatResult execute(ChatRequest request) {
    try {
      return strategy.call(client, configuration, request);
    } catch (ConnectorException e) {
      // the family strategy builds its request params (running content conversion, which can
      // throw an already-coded ConnectorException, e.g. OpenAiContentConverter's unsupported
      // content type) inside strategy.call(); re-throw verbatim so it isn't double-wrapped as a
      // generic "Model call failed" below.
      throw e;
    } catch (Exception e) {
      final String detail =
          Optional.ofNullable(e.getMessage())
              .filter(m -> !m.isBlank())
              .orElseGet(() -> e.getClass().getSimpleName());
      throw new ConnectorException(
          ERROR_CODE_FAILED_MODEL_CALL, "Model call failed: %s".formatted(detail), e);
    }
  }

  @Override
  public void close() {
    try {
      client.close();
    } catch (Exception e) {
      LOG.warn("Failed to close OpenAIClient", e);
    }
  }
}
