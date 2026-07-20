/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import io.camunda.connector.agenticai.aiagent.capabilities.ModelCapabilitiesResolver;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelApi;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.V2ChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.OpenAiApiFamilyStrategy;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsRequestConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsResponseConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsStrategy;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsStreamAssembler;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.responses.OpenAiResponsesRequestConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.responses.OpenAiResponsesResponseConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.responses.OpenAiResponsesStrategy;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.responses.OpenAiResponsesStreamAssembler;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModel;
import io.camunda.connector.agenticai.aiagent.transport.HttpTransportSupport;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ChatModelApiFactory} for the OpenAI wire formats' {@code direct} (API key) and {@code
 * compatible} (OpenAI-compatible gateway) backends, for both the Responses and Chat Completions api
 * families.
 */
public class OpenAiChatModelApiFactory implements ChatModelApiFactory {

  private static final Logger LOG = LoggerFactory.getLogger(OpenAiChatModelApiFactory.class);

  private final HttpTransportSupport transport;
  private final ModelCapabilitiesResolver capabilitiesResolver;
  private final ObjectMapper objectMapper;

  public OpenAiChatModelApiFactory(
      HttpTransportSupport transport,
      ModelCapabilitiesResolver capabilitiesResolver,
      ObjectMapper objectMapper) {
    this.transport = transport;
    this.capabilitiesResolver = capabilitiesResolver;
    this.objectMapper = objectMapper;
  }

  @Override
  public boolean supports(ChatModelApiConfiguration configuration) {
    return configuration instanceof V2ChatModelApiConfiguration llm
        && llm.configuration() instanceof OpenAiChatModel;
  }

  @Override
  public ChatModelApi create(ChatModelApiConfiguration configuration) {
    final var llm = (V2ChatModelApiConfiguration) configuration;
    final var model = (OpenAiChatModel) llm.configuration();
    final var connection = model.openai();
    final String familyKey = model.apiFamilyKey();
    final String backendType = connection.backend().type();
    final var timeout = connection.timeouts() != null ? connection.timeouts().timeout() : null;

    final OpenAiModelCapabilities capabilities =
        capabilitiesResolver.resolve(
            familyKey,
            connection.model().model(),
            backendType,
            Optional.ofNullable(model.capabilityOverride()),
            OpenAiModelCapabilitiesData.class);
    final boolean modelMatched =
        capabilitiesResolver.matches(familyKey, connection.model().model(), backendType);

    LOG.debug(
        "Resolved model capabilities for api-family={}, model={}, backend={}, matched={}: {}",
        familyKey,
        connection.model().model(),
        backendType,
        modelMatched,
        capabilities);

    final OpenAIClient client =
        new OpenAiOkHttpClientFactory(connection.backend(), timeout, transport).create();
    final var contentConverter = new OpenAiContentConverter(objectMapper);
    final OpenAiApiFamilyStrategy strategy =
        switch (connection.apiFamily()) {
          case RESPONSES ->
              new OpenAiResponsesStrategy(
                  new OpenAiResponsesRequestConverter(contentConverter, objectMapper),
                  new OpenAiResponsesResponseConverter(objectMapper),
                  OpenAiResponsesStreamAssembler.accumulating());
          case COMPLETIONS ->
              new OpenAiCompletionsStrategy(
                  new OpenAiCompletionsRequestConverter(contentConverter, objectMapper),
                  new OpenAiCompletionsResponseConverter(objectMapper),
                  OpenAiCompletionsStreamAssembler.accumulating());
        };
    return new OpenAiChatModelApi(client, strategy, capabilities, modelMatched);
  }
}
