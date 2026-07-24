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
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelFactory;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicCompatibleBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.shared.CompatibleAuthentication.CompatibleApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.transport.HttpTransportSupport;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.net.URI;
import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * {@link ChatModelFactory} for the Anthropic Messages wire format's {@code anthropic-api} (direct
 * API key) and {@code compatible} (Anthropic-compatible API) backends.
 *
 * <p>The Bedrock backend is deliberately not yet supported here; such configurations still fail
 * loud via the registry until a Bedrock-backed implementation exists to serve them.
 */
public class AnthropicChatModelApiFactory implements ChatModelFactory {

  private final HttpTransportSupport transport;
  private final AnthropicMessageRequestConverter requestConverter;
  private final AnthropicMessageResponseConverter responseConverter;

  public AnthropicChatModelApiFactory(HttpTransportSupport transport, ObjectMapper objectMapper) {
    this.transport = transport;
    this.requestConverter =
        new AnthropicMessageRequestConverter(new AnthropicContentConverter(objectMapper));
    this.responseConverter = new AnthropicMessageResponseConverter(objectMapper);
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

    final var client = buildClient(connection.backend(), timeout, transport);
    return new AnthropicChatModelApi(client, requestConverter, responseConverter);
  }

  /**
   * Builds an {@link AnthropicClient} backed by the vendor SDK's OkHttp transport for both the
   * {@code anthropic-api} (direct API key) and {@code compatible} (Anthropic-compatible API)
   * backends, applying the configured timeout and the shared, provider-neutral {@link
   * HttpTransportSupport} proxy resolution.
   */
  private static AnthropicClient buildClient(
      AnthropicBackend backend, @Nullable Duration timeout, HttpTransportSupport transport) {
    final var builder = AnthropicOkHttpClient.builder();

    if (backend instanceof AnthropicApiBackend direct) {
      builder.apiKey(direct.apiKey());
    } else if (backend instanceof AnthropicCompatibleBackend compatible) {
      applyCompatibleBackend(builder, compatible);
    }

    if (timeout != null) {
      builder.timeout(timeout);
    }

    final String scheme =
        backend instanceof AnthropicCompatibleBackend compatible
            ? URI.create(compatible.endpoint()).getScheme()
            : ProxyConfiguration.SCHEME_HTTPS;
    transport
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

  private static void applyCompatibleBackend(
      AnthropicOkHttpClient.Builder builder, AnthropicCompatibleBackend compatible) {
    builder.baseUrl(compatible.endpoint());

    if (compatible.compatibleAuthentication()
        instanceof CompatibleApiKeyAuthentication apiKeyAuth) {
      builder.apiKey(apiKeyAuth.apiKey());
    }
  }
}
