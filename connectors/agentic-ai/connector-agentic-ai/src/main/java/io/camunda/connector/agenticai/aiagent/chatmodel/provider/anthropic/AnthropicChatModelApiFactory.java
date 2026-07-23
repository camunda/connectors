/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelFactory;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicCompatibleBackend;
import io.camunda.connector.agenticai.aiagent.transport.HttpTransportSupport;

/**
 * {@link ChatModelFactory} for the Anthropic Messages wire format's {@code anthropic-api} (direct
 * API key) and {@code compatible} (Anthropic-compatible API) backends.
 *
 * <p>The Bedrock backend is deliberately not yet supported here; such configurations still fail
 * loud via the registry until a Bedrock-backed implementation exists to serve them.
 */
public class AnthropicChatModelApiFactory implements ChatModelFactory {

  private final HttpTransportSupport transport;
  private final ObjectMapper objectMapper;

  public AnthropicChatModelApiFactory(HttpTransportSupport transport, ObjectMapper objectMapper) {
    this.transport = transport;
    this.objectMapper = objectMapper;
  }

  @Override
  public boolean supports(ChatModelConfiguration configuration) {
    return configuration instanceof AnthropicChatModelConfiguration anthropic
        && (anthropic.anthropic().backend() instanceof AnthropicApiBackend
            || anthropic.anthropic().backend() instanceof AnthropicCompatibleBackend);
  }

  @Override
  public ChatModel create(ChatModelConfiguration configuration) {
    final var model = (AnthropicChatModelConfiguration) configuration;
    final var connection = model.anthropic();
    final var timeout = connection.timeouts() != null ? connection.timeouts().timeout() : null;

    final var client =
        new AnthropicOkHttpClientFactory(connection.backend(), timeout, transport).create();
    final var contentConverter = new AnthropicContentConverter(objectMapper);
    final var requestConverter = new AnthropicMessageRequestConverter(contentConverter);
    final var responseConverter = new AnthropicMessageResponseConverter(objectMapper);
    return new AnthropicChatModelApi(client, requestConverter, responseConverter);
  }
}
