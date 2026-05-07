/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApi;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilitiesResolver;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration.ApiFamily;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration.OpenAiConnection;
import java.time.Duration;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

/**
 * Native OpenAI factory for the {@code openai} discriminator. Builds an {@link OpenAIClient}
 * (OkHttp transport) from {@link OpenAiProviderConfiguration} and instantiates an {@link
 * OpenAiChatCompletionsChatModelApi}.
 *
 * <p>Phase D will add the {@code apiFamily} branch: when the config selects {@code apiFamily =
 * RESPONSES}, the factory will build {@code OpenAiResponsesChatModelApi} instead, sharing this
 * client construction.
 */
public class OpenAiChatModelApiFactory implements ChatModelApiFactory<OpenAiProviderConfiguration> {

  public static final String API_FAMILY_COMPLETIONS = "openai-completions";
  public static final String API_FAMILY_RESPONSES = "openai-responses";

  private final ObjectMapper objectMapper;
  private final ModelCapabilitiesResolver capabilitiesResolver;
  @Nullable private final Duration defaultTimeout;

  public OpenAiChatModelApiFactory(
      ObjectMapper objectMapper,
      ModelCapabilitiesResolver capabilitiesResolver,
      @Nullable Duration defaultTimeout) {
    this.objectMapper = objectMapper;
    this.capabilitiesResolver = capabilitiesResolver;
    this.defaultTimeout = defaultTimeout;
  }

  @Override
  public String providerType() {
    return OpenAiProviderConfiguration.OPENAI_ID;
  }

  @Override
  public String apiFamily() {
    // Default — actual family depends on the per-call config, so this is informational only.
    return API_FAMILY_COMPLETIONS;
  }

  @Override
  public Class<OpenAiProviderConfiguration> configurationType() {
    return OpenAiProviderConfiguration.class;
  }

  @Override
  public ChatModelApi create(OpenAiProviderConfiguration configuration) {
    final var connection = configuration.openai();
    final var client = buildClient(connection);
    final var parameters = connection.model().parameters();
    final var maxTokens =
        parameters != null && parameters.maxCompletionTokens() != null
            ? parameters.maxCompletionTokens().longValue()
            : null;
    final var temperature = parameters != null ? parameters.temperature() : null;
    final var topP = parameters != null ? parameters.topP() : null;

    final var apiFamily =
        connection.apiFamily() == ApiFamily.RESPONSES
            ? API_FAMILY_RESPONSES
            : API_FAMILY_COMPLETIONS;
    final var capabilities =
        capabilitiesResolver.resolve(apiFamily, connection.model().model(), Optional.empty());

    return connection.apiFamily() == ApiFamily.RESPONSES
        ? new OpenAiResponsesChatModelApi(
            client,
            connection.model().model(),
            objectMapper,
            capabilities,
            maxTokens,
            temperature,
            topP)
        : new OpenAiChatCompletionsChatModelApi(
            client,
            connection.model().model(),
            objectMapper,
            capabilities,
            maxTokens,
            temperature,
            topP);
  }

  private OpenAIClient buildClient(OpenAiConnection connection) {
    final var builder = OpenAIOkHttpClient.builder();
    builder.apiKey(connection.authentication().apiKey());

    if (StringUtils.isNotBlank(connection.endpoint())) {
      builder.baseUrl(connection.endpoint());
    }
    if (StringUtils.isNotBlank(connection.authentication().organizationId())) {
      builder.organization(connection.authentication().organizationId());
    }
    if (StringUtils.isNotBlank(connection.authentication().projectId())) {
      builder.project(connection.authentication().projectId());
    }

    final var timeout = resolveTimeout(connection);
    if (timeout != null) {
      builder.timeout(timeout);
    }

    return builder.build();
  }

  @Nullable
  private Duration resolveTimeout(OpenAiConnection connection) {
    return Optional.ofNullable(connection.timeouts()).map(t -> t.timeout()).orElse(defaultTimeout);
  }
}
