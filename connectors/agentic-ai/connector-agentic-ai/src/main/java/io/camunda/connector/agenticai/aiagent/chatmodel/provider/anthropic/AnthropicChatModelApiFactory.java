/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.http.ProxyAuthenticator;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelFactory;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicCustomBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.shared.CustomEndpointAuthentication.ApiKeyAuthentication;
import io.camunda.connector.agenticai.common.AgenticAiHttpProxySupport;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public class AnthropicChatModelApiFactory implements ChatModelFactory {

  private final AgenticAiHttpProxySupport httpProxySupport;
  private final AnthropicMessageRequestConverter requestConverter;
  private final AnthropicMessageResponseConverter responseConverter;

  public AnthropicChatModelApiFactory(
      AgenticAiHttpProxySupport httpProxySupport,
      AnthropicMessageRequestConverter requestConverter,
      AnthropicMessageResponseConverter responseConverter) {
    this.httpProxySupport = httpProxySupport;
    this.requestConverter = requestConverter;
    this.responseConverter = responseConverter;
  }

  @Override
  public boolean supports(ChatModelConfiguration configuration) {
    return configuration instanceof AnthropicChatModelConfiguration;
  }

  @Override
  public ChatModel create(ChatModelConfiguration configuration) {
    final var model = (AnthropicChatModelConfiguration) configuration;
    final var connection = model.anthropic();
    final var timeout = connection.timeouts() != null ? connection.timeouts().timeout() : null;

    final var client = buildClient(connection.backend(), timeout, httpProxySupport);
    return new AnthropicChatModelApi(client, model, requestConverter, responseConverter);
  }

  private static AnthropicClient buildClient(
      AnthropicBackend backend,
      @Nullable Duration timeout,
      AgenticAiHttpProxySupport httpProxySupport) {
    final var builder = AnthropicOkHttpClient.builder();

    switch (backend) {
      case AnthropicApiBackend apiBackend -> applyApiBackend(builder, apiBackend);
      case AnthropicCustomBackend custom -> applyCustomBackend(builder, custom);
    }

    if (timeout != null) {
      builder.timeout(timeout);
    }

    final String scheme =
        configuredEndpoint(backend).map(endpoint -> URI.create(endpoint).getScheme()).orElse(null);
    httpProxySupport
        .okHttpProxy(scheme != null ? scheme : ProxyConfiguration.SCHEME_HTTPS)
        .ifPresent(
            p -> {
              builder.proxy(p.proxy());
              if (p.hasCredentials()) {
                builder.proxyAuthenticator(ProxyAuthenticator.basic(p.username(), p.password()));
              }
            });
    return builder.build();
  }

  private static void applyApiBackend(
      AnthropicOkHttpClient.Builder builder, AnthropicApiBackend apiBackend) {
    builder.apiKey(apiBackend.anthropic().apiKey());

    if (apiBackend.anthropic().endpoint() != null) {
      builder.baseUrl(apiBackend.anthropic().endpoint());
    }
  }

  private static void applyCustomBackend(
      AnthropicOkHttpClient.Builder builder, AnthropicCustomBackend custom) {
    builder.baseUrl(custom.custom().endpoint());

    if (custom.custom().authentication() instanceof ApiKeyAuthentication apiKeyAuth) {
      builder.apiKey(apiKeyAuth.apiKey());
    }
  }

  /**
   * The base URL actually configured for this backend, if any: the {@code custom} backend's
   * endpoint is always set, while the {@code anthropic-api} backend's hidden endpoint override is
   * usually unset (the SDK then defaults to the production Anthropic API).
   */
  private static Optional<String> configuredEndpoint(AnthropicBackend backend) {
    return switch (backend) {
      case AnthropicApiBackend apiBackend -> Optional.ofNullable(apiBackend.anthropic().endpoint());
      case AnthropicCustomBackend custom -> Optional.of(custom.custom().endpoint());
    };
  }
}
