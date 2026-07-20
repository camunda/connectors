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
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModel.AnthropicBackend.AnthropicDirectBackend;
import io.camunda.connector.agenticai.aiagent.transport.HttpTransportSupport;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Builds an {@link AnthropicClient} backed by the vendor SDK's OkHttp transport for the {@code
 * direct} (API key) backend, applying the configured timeout and the shared, provider-neutral
 * {@link HttpTransportSupport} proxy resolution.
 */
public class AnthropicOkHttpClientFactory implements AnthropicClientFactory {

  private final String apiKey;
  private final @Nullable String baseUrl;
  private final @Nullable Duration timeout;
  private final Optional<HttpTransportSupport.OkHttpProxy> proxy;

  public AnthropicOkHttpClientFactory(
      AnthropicDirectBackend backend, @Nullable Duration timeout, HttpTransportSupport transport) {
    this.apiKey = backend.apiKey();
    this.baseUrl = backend.endpoint();
    this.timeout = timeout;
    final String scheme =
        baseUrl != null ? URI.create(baseUrl).getScheme() : ProxyConfiguration.SCHEME_HTTPS;
    this.proxy = transport.okHttpProxy(scheme != null ? scheme : ProxyConfiguration.SCHEME_HTTPS);
  }

  @Override
  public AnthropicClient create() {
    final var builder = AnthropicOkHttpClient.builder().apiKey(apiKey);
    if (baseUrl != null && !baseUrl.isBlank()) {
      builder.baseUrl(baseUrl);
    }
    if (timeout != null) {
      builder.timeout(timeout);
    }
    proxy.ifPresent(
        p -> {
          builder.proxy(p.proxy());
          if (p.hasCredentials()) {
            builder.proxyAuthenticator(ProxyAuthenticator.basic(p.username(), p.password()));
          }
        });
    return builder.build();
  }
}
