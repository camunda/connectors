/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.anthropic;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApi;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.framework.api.LlmProviderChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilities;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilitiesResolver;
import io.camunda.connector.agenticai.aiagent.framework.transport.HttpTransportSupport;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.AnthropicBackend.AnthropicDirectBackend;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Native {@link ChatModelApiFactory} for the Anthropic Messages wire format's {@code direct} (API
 * key) backend. Registers below the LangChain4j bridge's {@link
 * io.camunda.connector.agenticai.aiagent.framework.langchain4j.Langchain4JChatModelApiFactory}
 * precedence ({@code getOrder() == 100 < 1000}) so it takes over resolution for the configurations
 * it supports.
 *
 * <p>The {@code bedrock} backend is deliberately not yet supported here; until a native Bedrock
 * implementation exists, such configurations still fail loud via the registry (neither this factory
 * nor the bridge, which only serves the legacy {@code ProviderChatModelApiConfiguration}, supports
 * them).
 */
public class AnthropicChatModelApiFactory implements ChatModelApiFactory {

  private static final Logger LOG = LoggerFactory.getLogger(AnthropicChatModelApiFactory.class);

  static final int ORDER = 100;
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
    return configuration instanceof LlmProviderChatModelApiConfiguration llm
        && llm.configuration() instanceof AnthropicChatModel anthropic
        && anthropic.anthropic().backend() instanceof AnthropicDirectBackend;
  }

  @Override
  public ChatModelApi create(ChatModelApiConfiguration configuration) {
    final var llm = (LlmProviderChatModelApiConfiguration) configuration;
    final var model = (AnthropicChatModel) llm.configuration();
    final var connection = model.anthropic();
    final var direct = (AnthropicDirectBackend) connection.backend();
    final var timeout = connection.timeouts() != null ? connection.timeouts().timeout() : null;

    final ModelCapabilities capabilities =
        capabilitiesResolver.resolve(
            API_FAMILY,
            connection.model().model(),
            direct.type(),
            Optional.ofNullable(model.capabilityOverride()));

    LOG.debug(
        "Resolved model capabilities for api-family={}, model={}, backend={}: {}",
        API_FAMILY,
        connection.model().model(),
        direct.type(),
        capabilities);

    final var clientFactory = new AnthropicOkHttpClientFactory(direct, timeout, transport);
    final var contentConverter = new AnthropicContentConverter(objectMapper);
    final var requestConverter = new AnthropicMessageRequestConverter(contentConverter);
    final var responseConverter = new AnthropicMessageResponseConverter(objectMapper);
    return new AnthropicChatModelApi(
        clientFactory, requestConverter, responseConverter, capabilities);
  }

  @Override
  public int getOrder() {
    return ORDER;
  }
}
