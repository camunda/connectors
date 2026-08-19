/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai;

import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.azure.credential.AzureApiKeyCredential;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.ProxyAuthenticator;
import com.openai.credential.BearerTokenCredential;
import com.openai.credential.Credential;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.OpenAiApiFamilyStrategy;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiCompletionsApi;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiResponsesApi;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.FoundryAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiCustomBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiFoundryBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiCustomEndpointAuthentication.ApiKeyAuthentication;
import io.camunda.connector.agenticai.common.AgenticAiHttpProxySupport;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * {@link ChatModelFactory} for the native OpenAI provider's {@code openai-api} (API key), {@code
 * foundry} (Microsoft Foundry / Azure OpenAI) and {@code custom} (OpenAI-compatible endpoint)
 * backends, for both the Responses and Chat Completions API families. Client construction is folded
 * in here rather than a separate client-factory class.
 */
public class OpenAiChatModelFactory implements ChatModelFactory {

  /** Scope requested when acquiring a Microsoft Entra ID token for Azure OpenAI / Foundry. */
  private static final String AZURE_COGNITIVE_SERVICES_SCOPE =
      "https://cognitiveservices.azure.com/.default";

  private final AgenticAiHttpProxySupport httpProxySupport;
  private final OpenAiApiFamilyStrategy completionsStrategy;
  private final OpenAiApiFamilyStrategy responsesStrategy;
  private final FoundryCredentialCache foundryCredentialCache;

  public OpenAiChatModelFactory(
      AgenticAiHttpProxySupport httpProxySupport,
      OpenAiApiFamilyStrategy completionsStrategy,
      OpenAiApiFamilyStrategy responsesStrategy,
      FoundryCredentialCache foundryCredentialCache) {
    this.httpProxySupport = httpProxySupport;
    this.completionsStrategy = completionsStrategy;
    this.responsesStrategy = responsesStrategy;
    this.foundryCredentialCache = foundryCredentialCache;
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
        buildClient(connection.backend(), timeout, httpProxySupport, foundryCredentialCache);
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
      FoundryCredentialCache foundryCredentialCache) {
    final var builder = OpenAIOkHttpClient.builder();

    switch (backend) {
      case OpenAiApiBackend apiBackend -> applyApiBackend(builder, apiBackend);
      case OpenAiFoundryBackend foundryBackend ->
          applyFoundryBackend(builder, foundryBackend, foundryCredentialCache);
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
   * Applies the {@code foundry} backend: base URL plus, per authentication variant, either an Azure
   * API-key credential or a Microsoft Entra ID bearer-token credential backed by a cached
   * azure-identity {@link TokenCredential} (see {@link FoundryCredentialCache}). The SDK detects
   * the Azure API surface (legacy vs. unified) automatically from the endpoint's hostname; {@code
   * apiVersion} is only wired when explicitly set, as an escape hatch for pinning a specific
   * legacy-style API version.
   */
  private static void applyFoundryBackend(
      OpenAIOkHttpClient.Builder builder,
      OpenAiFoundryBackend foundryBackend,
      FoundryCredentialCache foundryCredentialCache) {
    final var foundry = foundryBackend.foundry();
    builder.baseUrl(foundry.endpoint());

    if (foundry.apiVersion() != null && !foundry.apiVersion().isBlank()) {
      builder.azureServiceVersion(AzureOpenAIServiceVersion.fromString(foundry.apiVersion()));
    }

    switch (foundry.authentication()) {
      case FoundryAuthentication.ApiKeyAuthentication apiKeyAuth ->
          builder.credential(AzureApiKeyCredential.create(apiKeyAuth.apiKey()));
      case FoundryAuthentication.ClientCredentialsAuthentication auth ->
          builder.credential(
              entraIdBearerTokenCredential(
                  foundryCredentialCache.getOrCreate(
                      clientCredentialsCacheKey(auth), () -> buildTokenCredential(auth))));
      case FoundryAuthentication.ManagedIdentityAuthentication auth ->
          builder.credential(
              entraIdBearerTokenCredential(
                  foundryCredentialCache.getOrCreate(
                      managedIdentityCacheKey(auth), () -> buildTokenCredential(auth))));
    }
  }

  /**
   * Wraps an azure-identity {@link TokenCredential} as an openai-java {@link Credential}: the
   * supplier is invoked fresh on every request, so this never caches a token itself, relying
   * entirely on the wrapped credential's own token cache and refresh logic.
   */
  private static Credential entraIdBearerTokenCredential(TokenCredential tokenCredential) {
    return BearerTokenCredential.create(
        () ->
            Objects.requireNonNull(
                    tokenCredential
                        .getToken(
                            new TokenRequestContext().addScopes(AZURE_COGNITIVE_SERVICES_SCOPE))
                        .block())
                .getToken());
  }

  private static TokenCredential buildTokenCredential(
      FoundryAuthentication.ClientCredentialsAuthentication auth) {
    final var clientSecretCredentialBuilder =
        new ClientSecretCredentialBuilder()
            .clientId(auth.clientId())
            .clientSecret(auth.clientSecret())
            .tenantId(auth.tenantId());
    if (auth.authorityHost() != null && !auth.authorityHost().isBlank()) {
      clientSecretCredentialBuilder.authorityHost(auth.authorityHost());
    }
    return clientSecretCredentialBuilder.build();
  }

  private static TokenCredential buildTokenCredential(
      FoundryAuthentication.ManagedIdentityAuthentication auth) {
    final var managedIdentityCredentialBuilder = new ManagedIdentityCredentialBuilder();
    if (auth.managedIdentityClientId() != null && !auth.managedIdentityClientId().isBlank()) {
      managedIdentityCredentialBuilder.clientId(auth.managedIdentityClientId());
    }
    return managedIdentityCredentialBuilder.build();
  }

  private static String clientCredentialsCacheKey(
      FoundryAuthentication.ClientCredentialsAuthentication auth) {
    return String.join(
        "\0",
        "clientCredentials",
        auth.tenantId(),
        auth.clientId(),
        auth.clientSecret(),
        Objects.requireNonNullElse(auth.authorityHost(), ""));
  }

  private static String managedIdentityCacheKey(
      FoundryAuthentication.ManagedIdentityAuthentication auth) {
    return String.join(
        "\0", "managedIdentity", Objects.requireNonNullElse(auth.managedIdentityClientId(), ""));
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
