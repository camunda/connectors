/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.capabilities.ModelCapabilitiesResolver;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelApi;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModel.AnthropicBackend.AnthropicCompatibleBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModel.AnthropicBackend.AnthropicDirectBackend;
import io.camunda.connector.agenticai.aiagent.transport.HttpTransportSupport;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ChatModelApiFactory} for the Anthropic Messages wire format's {@code direct} (API key) and
 * {@code compatible} (Anthropic-compatible API) backends.
 *
 * <p>The {@code bedrock} backend is deliberately not yet supported here; such configurations still
 * fail loud via the registry until a Bedrock-backed implementation exists to serve them.
 */
public class AnthropicChatModelApiFactory implements ChatModelApiFactory {

  private static final Logger LOG = LoggerFactory.getLogger(AnthropicChatModelApiFactory.class);

  static final String API_FAMILY = "anthropic-messages";

  private final HttpTransportSupport transport;
  private final ModelCapabilitiesResolver capabilitiesResolver;
  private final ObjectMapper objectMapper;

  public AnthropicChatModelApiFactory(
      HttpTransportSupport transport,
      ModelCapabilitiesResolver capabilitiesResolver,
      ObjectMapper objectMapper) {
    this.transport = transport;
    this.capabilitiesResolver = capabilitiesResolver;
    this.objectMapper = objectMapper;
  }

  @Override
  public boolean supports(ChatModelApiConfiguration configuration) {
    return configuration instanceof AnthropicChatModel anthropic
        && (anthropic.anthropic().backend() instanceof AnthropicDirectBackend
            || anthropic.anthropic().backend() instanceof AnthropicCompatibleBackend);
  }

  @Override
  public ChatModelApi create(ChatModelApiConfiguration configuration) {
    final var model = (AnthropicChatModel) configuration;
    final var connection = model.anthropic();
    final String backendType = connection.backend().type();
    final var timeout = connection.timeouts() != null ? connection.timeouts().timeout() : null;

    final AnthropicModelCapabilities capabilities =
        capabilitiesResolver.resolve(
            API_FAMILY,
            connection.model().model(),
            backendType,
            Optional.ofNullable(model.capabilityOverride()),
            AnthropicModelCapabilitiesData.class);
    final boolean modelMatched =
        capabilitiesResolver.matches(API_FAMILY, connection.model().model(), backendType);

    LOG.debug(
        "Resolved model capabilities for api-family={}, model={}, backend={}, matched={}: {}",
        API_FAMILY,
        connection.model().model(),
        backendType,
        modelMatched,
        capabilities);

    final var client =
        new AnthropicOkHttpClientFactory(connection.backend(), timeout, transport).create();
    final var contentConverter = new AnthropicContentConverter(objectMapper);
    final var requestConverter = new AnthropicMessageRequestConverter(contentConverter);
    final var responseConverter = new AnthropicMessageResponseConverter(objectMapper);
    return new AnthropicChatModelApi(
        client, requestConverter, responseConverter, capabilities, modelMatched);
  }
}
