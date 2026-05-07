/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApi;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.AnthropicConnection;
import java.time.Duration;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

/**
 * Native Anthropic Messages factory for the {@code anthropic} discriminator (direct backend).
 * Builds an {@link AnthropicClient} using the published OkHttp transport from a {@link
 * AnthropicProviderConfiguration} and produces an {@link AnthropicMessagesChatModelApi} per
 * invocation.
 *
 * <p>Replaces the LangChain4j bridge factory at this discriminator. Cloud backends (Bedrock /
 * Vertex / Foundry) will land in Phase G via additional factory variants.
 */
public class AnthropicMessagesChatModelApiFactory
    implements ChatModelApiFactory<AnthropicProviderConfiguration> {

  public static final String API_FAMILY = "anthropic-messages";

  @Nullable private final Duration defaultTimeout;

  public AnthropicMessagesChatModelApiFactory(@Nullable Duration defaultTimeout) {
    this.defaultTimeout = defaultTimeout;
  }

  @Override
  public String providerType() {
    return AnthropicProviderConfiguration.ANTHROPIC_ID;
  }

  @Override
  public String apiFamily() {
    return API_FAMILY;
  }

  @Override
  public Class<AnthropicProviderConfiguration> configurationType() {
    return AnthropicProviderConfiguration.class;
  }

  @Override
  public ChatModelApi create(AnthropicProviderConfiguration configuration) {
    final var connection = configuration.anthropic();
    final var client = buildClient(connection);
    final var parameters = connection.model().parameters();
    return new AnthropicMessagesChatModelApi(
        client,
        connection.model().model(),
        parameters != null && parameters.maxTokens() != null
            ? parameters.maxTokens().longValue()
            : null,
        parameters != null ? parameters.temperature() : null,
        parameters != null ? parameters.topP() : null,
        parameters != null && parameters.topK() != null ? parameters.topK().longValue() : null);
  }

  private AnthropicClient buildClient(AnthropicConnection connection) {
    final var builder =
        AnthropicOkHttpClient.builder().apiKey(connection.authentication().apiKey());

    if (StringUtils.isNotBlank(connection.endpoint())) {
      builder.baseUrl(normalizeBaseUrl(connection.endpoint()));
    }

    final var timeout = resolveTimeout(connection);
    if (timeout != null) {
      builder.timeout(timeout);
    }

    return builder.build();
  }

  @Nullable
  private Duration resolveTimeout(AnthropicConnection connection) {
    return Optional.ofNullable(connection.timeouts()).map(t -> t.timeout()).orElse(defaultTimeout);
  }

  /**
   * The {@code anthropic-java} SDK expects {@code baseUrl} to be the host without the {@code /v1}
   * prefix and appends the full {@code /v1/messages} path itself. The LangChain4j Anthropic client
   * used the opposite convention (callers set {@code https://host/v1}, L4J appends just {@code
   * /messages}). Strip a trailing {@code /v1} (with or without trailing slash) so existing
   * element-template configurations keep working when users switch to the native impl.
   */
  static String normalizeBaseUrl(String endpoint) {
    final var trimmed =
        endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
    return trimmed.endsWith("/v1") ? trimmed.substring(0, trimmed.length() - 3) : trimmed;
  }
}
