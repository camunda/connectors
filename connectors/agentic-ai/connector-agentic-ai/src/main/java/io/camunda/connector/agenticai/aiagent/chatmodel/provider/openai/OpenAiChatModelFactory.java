/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.ProxyAuthenticator;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.OpenAiApiFamilyStrategy;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiCompletionsApi;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiResponsesApi;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiCustomBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiCustomEndpointAuthentication.ApiKeyAuthentication;
import io.camunda.connector.agenticai.common.AgenticAiHttpProxySupport;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * {@link ChatModelFactory} for the native OpenAI provider's {@code openai-api} (API key) and {@code
 * custom} (OpenAI-compatible endpoint) backends, for both the Responses and Chat Completions API
 * families. Client construction is folded in here rather than a separate client-factory class.
 */
public class OpenAiChatModelFactory implements ChatModelFactory {

  private final AgenticAiHttpProxySupport httpProxySupport;
  private final OpenAiApiFamilyStrategy completionsStrategy;
  private final OpenAiApiFamilyStrategy responsesStrategy;

  public OpenAiChatModelFactory(
      AgenticAiHttpProxySupport httpProxySupport,
      OpenAiApiFamilyStrategy completionsStrategy,
      OpenAiApiFamilyStrategy responsesStrategy) {
    this.httpProxySupport = httpProxySupport;
    this.completionsStrategy = completionsStrategy;
    this.responsesStrategy = responsesStrategy;
  }

  @Override
  public boolean supports(ChatModelConfiguration configuration) {
    return configuration instanceof OpenAiChatModelConfiguration;
  }

  @Override
  public ChatModel create(ChatModelConfiguration configuration) {
    final var model = (OpenAiChatModelConfiguration) configuration;
    final var connection = model.openai();
    final var timeout = connection.timeouts() != null ? connection.timeouts().timeout() : null;

    final var client = buildClient(connection.backend(), timeout, httpProxySupport);
    final var strategy = strategyFor(connection.api());
    return new OpenAiChatModel(client, model, strategy);
  }

  private OpenAiApiFamilyStrategy strategyFor(OpenAiChatModelConfiguration.OpenAiApi api) {
    return switch (api) {
      case OpenAiCompletionsApi ignored -> completionsStrategy;
      case OpenAiResponsesApi ignored -> responsesStrategy;
    };
  }

  private static OpenAIClient buildClient(
      OpenAiBackend backend,
      @Nullable Duration timeout,
      AgenticAiHttpProxySupport httpProxySupport) {
    final var builder = OpenAIOkHttpClient.builder();

    switch (backend) {
      case OpenAiApiBackend apiBackend -> applyApiBackend(builder, apiBackend);
      case OpenAiCustomBackend custom -> applyCustomBackend(builder, custom);
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
      OpenAIOkHttpClient.Builder builder, OpenAiApiBackend apiBackend) {
    final var openai = apiBackend.openai();
    builder.apiKey(openai.apiKey());

    if (openai.organizationId() != null && !openai.organizationId().isBlank()) {
      builder.organization(openai.organizationId());
    }
    if (openai.projectId() != null && !openai.projectId().isBlank()) {
      builder.project(openai.projectId());
    }
    if (openai.endpoint() != null) {
      builder.baseUrl(openai.endpoint());
    }
  }

  private static void applyCustomBackend(
      OpenAIOkHttpClient.Builder builder, OpenAiCustomBackend custom) {
    final var connection = custom.custom();
    builder.baseUrl(connection.endpoint());

    switch (connection.authentication()) {
      case ApiKeyAuthentication apiKeyAuth -> builder.apiKey(apiKeyAuth.apiKey());
    }
  }

  /**
   * The base URL actually configured for this backend, if any: the {@code custom} backend's
   * endpoint is always set, while the {@code openai-api} backend's hidden endpoint override is
   * usually unset (the SDK then defaults to the production OpenAI API).
   */
  private static Optional<String> configuredEndpoint(OpenAiBackend backend) {
    return switch (backend) {
      case OpenAiApiBackend apiBackend -> Optional.ofNullable(apiBackend.openai().endpoint());
      case OpenAiCustomBackend custom -> Optional.of(custom.custom().endpoint());
    };
  }
}
