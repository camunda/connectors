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
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModel.AnthropicBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModel.AnthropicBackend.AnthropicCompatibleBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModel.AnthropicBackend.AnthropicDirectBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.shared.CompatibleAuthentication.CompatibleApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.transport.HttpTransportSupport;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Builds an {@link AnthropicClient} backed by the vendor SDK's OkHttp transport for both the {@code
 * direct} (API key) and {@code compatible} (Anthropic-compatible API) backends, applying the
 * configured timeout and the shared, provider-neutral {@link HttpTransportSupport} proxy
 * resolution.
 */
public class AnthropicOkHttpClientFactory implements AnthropicClientFactory {

  /**
   * Placeholder API key sent for {@code compatible} backends configured with no authentication. The
   * SDK client builder requires a non-blank api key to build even though Anthropic-compatible APIs
   * without authentication ignore it entirely.
   */
  private static final String NO_AUTH_PLACEHOLDER_API_KEY = "not-required";

  private final AnthropicBackend backend;
  private final @Nullable Duration timeout;
  private final Optional<HttpTransportSupport.OkHttpProxy> proxy;

  public AnthropicOkHttpClientFactory(
      AnthropicBackend backend, @Nullable Duration timeout, HttpTransportSupport transport) {
    this.backend = backend;
    this.timeout = timeout;
    final String scheme =
        backend instanceof AnthropicCompatibleBackend compatible
            ? URI.create(compatible.endpoint()).getScheme()
            : ProxyConfiguration.SCHEME_HTTPS;
    this.proxy = transport.okHttpProxy(scheme != null ? scheme : ProxyConfiguration.SCHEME_HTTPS);
  }

  @Override
  public AnthropicClient create() {
    final var builder = AnthropicOkHttpClient.builder();

    if (backend instanceof AnthropicDirectBackend direct) {
      applyDirectBackend(builder, direct);
    } else if (backend instanceof AnthropicCompatibleBackend compatible) {
      applyCompatibleBackend(builder, compatible);
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

  private void applyDirectBackend(
      AnthropicOkHttpClient.Builder builder, AnthropicDirectBackend direct) {
    builder.apiKey(direct.apiKey());
    if (direct.endpoint() != null && !direct.endpoint().isBlank()) {
      builder.baseUrl(direct.endpoint());
    }
  }

  private void applyCompatibleBackend(
      AnthropicOkHttpClient.Builder builder, AnthropicCompatibleBackend compatible) {
    builder.baseUrl(compatible.endpoint());

    final String apiKey =
        compatible.compatibleAuthentication() instanceof CompatibleApiKeyAuthentication apiKeyAuth
            ? apiKeyAuth.apiKey()
            : NO_AUTH_PLACEHOLDER_API_KEY;
    builder.apiKey(apiKey);

    if (compatible.headers() != null) {
      for (Map.Entry<String, String> header : compatible.headers().entrySet()) {
        builder.putHeader(header.getKey(), header.getValue());
      }
    }
    if (compatible.queryParameters() != null) {
      for (Map.Entry<String, String> queryParameter : compatible.queryParameters().entrySet()) {
        builder.putQueryParam(queryParameter.getKey(), queryParameter.getValue());
      }
    }
  }
}
