/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai;

import com.openai.azure.AzureOpenAIServiceVersion;
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
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiFoundryBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiCustomEndpointAuthentication.ApiKeyAuthentication;
import io.camunda.connector.agenticai.common.AgenticAiHttpProxySupport;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * {@link ChatModelFactory} for the native OpenAI provider's {@code openai-api} (API key), {@code
 * foundry} (Microsoft Foundry / Azure OpenAI) and {@code custom} (OpenAI-compatible endpoint)
 * backends, for both the Responses and Chat Completions API families. Client construction is folded
 * in here rather than a separate client-factory class; {@code foundry}'s Azure/Entra ID specifics
 * are delegated to {@link OpenAiFoundryCredentialResolver} to keep this class
 * provider-shape-agnostic.
 */
public class OpenAiChatModelFactory implements ChatModelFactory {

  private final AgenticAiHttpProxySupport httpProxySupport;
  private final OpenAiApiFamilyStrategy completionsStrategy;
  private final OpenAiApiFamilyStrategy responsesStrategy;
  private final OpenAiFoundryCredentialResolver openAiFoundryCredentialResolver;

  public OpenAiChatModelFactory(
      AgenticAiHttpProxySupport httpProxySupport,
      OpenAiApiFamilyStrategy completionsStrategy,
      OpenAiApiFamilyStrategy responsesStrategy,
      OpenAiFoundryCredentialResolver openAiFoundryCredentialResolver) {
    this.httpProxySupport = httpProxySupport;
    this.completionsStrategy = completionsStrategy;
    this.responsesStrategy = responsesStrategy;
    this.openAiFoundryCredentialResolver = openAiFoundryCredentialResolver;
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

    final var client =
        buildClient(
            connection.backend(), timeout, httpProxySupport, openAiFoundryCredentialResolver);
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
      AgenticAiHttpProxySupport httpProxySupport,
      OpenAiFoundryCredentialResolver openAiFoundryCredentialResolver) {
    final var builder = OpenAIOkHttpClient.builder();

    switch (backend) {
      case OpenAiApiBackend apiBackend -> applyApiBackend(builder, apiBackend);
      case OpenAiFoundryBackend foundryBackend ->
          applyFoundryBackend(builder, foundryBackend, openAiFoundryCredentialResolver);
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
   * Applies the {@code foundry} backend: base URL, an optional {@code apiVersion} pin, and the
   * {@link com.openai.credential.Credential} resolved by {@link OpenAiFoundryCredentialResolver}
   * for the configured authentication variant -- this class never builds or inspects that
   * credential itself. The SDK detects the Azure API surface (legacy vs. unified) automatically
   * from the endpoint's hostname; {@code apiVersion} is only wired when explicitly set, as an
   * escape hatch for pinning a specific legacy-style API version.
   */
  private static void applyFoundryBackend(
      OpenAIOkHttpClient.Builder builder,
      OpenAiFoundryBackend foundryBackend,
      OpenAiFoundryCredentialResolver openAiFoundryCredentialResolver) {
    final var foundry = foundryBackend.foundry();
    builder.baseUrl(foundry.endpoint());

    if (foundry.apiVersion() != null && !foundry.apiVersion().isBlank()) {
      builder.azureServiceVersion(AzureOpenAIServiceVersion.fromString(foundry.apiVersion()));
    }

    builder.credential(
        openAiFoundryCredentialResolver.credential(foundry.endpoint(), foundry.authentication()));
  }

  /**
   * The base URL actually configured for this backend, if any: the {@code custom} and {@code
   * foundry} backends' endpoints are always set, while the {@code openai-api} backend's hidden
   * endpoint override is usually unset (the SDK then defaults to the production OpenAI API).
   */
  private static Optional<String> configuredEndpoint(OpenAiBackend backend) {
    return switch (backend) {
      case OpenAiApiBackend apiBackend -> Optional.ofNullable(apiBackend.openai().endpoint());
      case OpenAiFoundryBackend foundryBackend -> Optional.of(foundryBackend.foundry().endpoint());
      case OpenAiCustomBackend custom -> Optional.of(custom.custom().endpoint());
    };
  }
}
