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
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiCompatibleProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiCompatibleProviderConfiguration.OpenAiCompatibleConnection;
import java.time.Duration;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

/**
 * Native factory for the {@code openaiCompatible} discriminator. Same {@link
 * OpenAiChatCompletionsChatModelApi} impl as the OpenAI-direct factory; only the OkHttp client
 * construction differs (custom baseUrl, optional API key, custom headers / query params).
 */
public class OpenAiCompatibleChatModelApiFactory
    implements ChatModelApiFactory<OpenAiCompatibleProviderConfiguration> {

  public static final String API_FAMILY = "openai-completions";

  private final ObjectMapper objectMapper;
  @Nullable private final Duration defaultTimeout;

  public OpenAiCompatibleChatModelApiFactory(
      ObjectMapper objectMapper, @Nullable Duration defaultTimeout) {
    this.objectMapper = objectMapper;
    this.defaultTimeout = defaultTimeout;
  }

  @Override
  public String providerType() {
    return OpenAiCompatibleProviderConfiguration.OPENAI_COMPATIBLE_ID;
  }

  @Override
  public String apiFamily() {
    return API_FAMILY;
  }

  @Override
  public Class<OpenAiCompatibleProviderConfiguration> configurationType() {
    return OpenAiCompatibleProviderConfiguration.class;
  }

  @Override
  public ChatModelApi create(OpenAiCompatibleProviderConfiguration configuration) {
    final var connection = configuration.openaiCompatible();
    final var client = buildClient(connection);
    final var parameters = connection.model().parameters();
    return new OpenAiChatCompletionsChatModelApi(
        client,
        connection.model().model(),
        objectMapper,
        parameters != null && parameters.maxCompletionTokens() != null
            ? parameters.maxCompletionTokens().longValue()
            : null,
        parameters != null ? parameters.temperature() : null,
        parameters != null ? parameters.topP() : null);
  }

  private OpenAIClient buildClient(OpenAiCompatibleConnection connection) {
    final var builder = OpenAIOkHttpClient.builder().baseUrl(connection.endpoint());

    final var auth = connection.authentication();
    final var apiKey =
        auth != null && StringUtils.isNotBlank(auth.apiKey()) ? auth.apiKey() : "no-key";
    builder.apiKey(apiKey);

    if (connection.headers() != null && !connection.headers().isEmpty()) {
      connection.headers().forEach(builder::putHeader);
    }
    if (connection.queryParameters() != null && !connection.queryParameters().isEmpty()) {
      connection.queryParameters().forEach(builder::putQueryParam);
    }

    final var timeout = resolveTimeout(connection);
    if (timeout != null) {
      builder.timeout(timeout);
    }

    return builder.build();
  }

  @Nullable
  private Duration resolveTimeout(OpenAiCompatibleConnection connection) {
    return Optional.ofNullable(connection.timeouts()).map(t -> t.timeout()).orElse(defaultTimeout);
  }
}
