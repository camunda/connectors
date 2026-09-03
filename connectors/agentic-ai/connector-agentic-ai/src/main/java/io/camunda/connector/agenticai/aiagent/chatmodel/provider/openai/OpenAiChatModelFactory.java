/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai;

import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.azure.AzureUrlPathMode;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.ProxyAuthenticator;
import com.openai.credential.BearerTokenCredential;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.authentication.oauth.OAuthClientCredentialsTokenResolver;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.OpenAiApiFamilyStrategy;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiCompletionsApi;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiResponsesApi;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiCustomBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiFoundryBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiCustomEndpointAuthentication.ApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiCustomEndpointAuthentication.OAuthClientCredentialsAuthentication;
import io.camunda.connector.agenticai.common.AgenticAiHttpProxySupport;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.net.URI;
import java.net.URISyntaxException;
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
  private final OAuthClientCredentialsTokenResolver oAuthClientCredentialsTokenResolver;

  public OpenAiChatModelFactory(
      AgenticAiHttpProxySupport httpProxySupport,
      OpenAiApiFamilyStrategy completionsStrategy,
      OpenAiApiFamilyStrategy responsesStrategy,
      OpenAiFoundryCredentialResolver openAiFoundryCredentialResolver,
      OAuthClientCredentialsTokenResolver oAuthClientCredentialsTokenResolver) {
    this.httpProxySupport = httpProxySupport;
    this.completionsStrategy = completionsStrategy;
    this.responsesStrategy = responsesStrategy;
    this.openAiFoundryCredentialResolver = openAiFoundryCredentialResolver;
    this.oAuthClientCredentialsTokenResolver = oAuthClientCredentialsTokenResolver;
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
            connection.backend(),
            timeout,
            httpProxySupport,
            openAiFoundryCredentialResolver,
            oAuthClientCredentialsTokenResolver);
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
      OpenAiFoundryCredentialResolver openAiFoundryCredentialResolver,
      OAuthClientCredentialsTokenResolver oAuthClientCredentialsTokenResolver) {
    final var builder = OpenAIOkHttpClient.builder();

    switch (backend) {
      case OpenAiApiBackend apiBackend -> applyApiBackend(builder, apiBackend);
      case OpenAiFoundryBackend foundryBackend ->
          applyFoundryBackend(builder, foundryBackend, openAiFoundryCredentialResolver);
      case OpenAiCustomBackend custom ->
          applyCustomBackend(builder, custom, oAuthClientCredentialsTokenResolver);
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
      OpenAIOkHttpClient.Builder builder,
      OpenAiCustomBackend custom,
      OAuthClientCredentialsTokenResolver oAuthClientCredentialsTokenResolver) {
    final var connection = custom.custom();
    builder.baseUrl(connection.endpoint());

    switch (connection.authentication()) {
      case ApiKeyAuthentication apiKeyAuth -> builder.apiKey(apiKeyAuth.apiKey());
      case OAuthClientCredentialsAuthentication oauth ->
          builder.credential(
              BearerTokenCredential.create(
                  () ->
                      oAuthClientCredentialsTokenResolver.resolveAccessToken(
                          toOAuthAuthentication(oauth))));
    }
  }

  private static io.camunda.connector.http.client.model.auth.OAuthAuthentication
      toOAuthAuthentication(OAuthClientCredentialsAuthentication oauth) {
    return new io.camunda.connector.http.client.model.auth.OAuthAuthentication(
        oauth.oauthTokenEndpoint(),
        oauth.clientId(),
        oauth.clientSecret(),
        oauth.audience(),
        oauth.clientAuthentication().oauthConstant(),
        oauth.scopes());
  }

  /**
   * Applies the {@code foundry} backend: base URL (normalized onto the unified OpenAI/v1 API
   * surface, see {@link #unifiedEndpoint(String)}), an optional {@code apiVersion} pin, and the
   * {@link com.openai.credential.Credential} resolved by {@link OpenAiFoundryCredentialResolver}
   * for the configured authentication variant -- this class never builds or inspects that
   * credential itself. {@code apiVersion} is only wired when explicitly set: the unified surface
   * uses implicit versioning, so it's an escape hatch for pinning a specific version rather than
   * something every request needs.
   *
   * <p>{@code azureUrlPathMode} is forced to {@link AzureUrlPathMode#UNIFIED} rather than left on
   * the SDK's default {@code AUTO} host-sniffing: the endpoint is already unconditionally
   * normalized onto {@code /openai/v1} above, and {@code AUTO}'s host allowlist doesn't recognize
   * Azure Government ({@code *.azure.us}) hosts as Azure at all, which would silently classify
   * those requests as non-Azure and skip the {@code apiVersion} escape hatch (and other
   * Azure-specific handling) regardless of path.
   */
  private static void applyFoundryBackend(
      OpenAIOkHttpClient.Builder builder,
      OpenAiFoundryBackend foundryBackend,
      OpenAiFoundryCredentialResolver openAiFoundryCredentialResolver) {
    final var foundry = foundryBackend.foundry();
    builder.baseUrl(unifiedEndpoint(foundry.endpoint()));
    builder.azureUrlPathMode(AzureUrlPathMode.UNIFIED);

    if (foundry.apiVersion() != null && !foundry.apiVersion().isBlank()) {
      builder.azureServiceVersion(AzureOpenAIServiceVersion.fromString(foundry.apiVersion()));
    }

    builder.credential(openAiFoundryCredentialResolver.credential(foundry.authentication()));
  }

  /**
   * Normalizes a Foundry {@code endpoint} onto the unified OpenAI/v1 API surface Microsoft
   * currently recommends for both classic Azure OpenAI ({@code *.openai.azure.com}) and Foundry
   * ({@code *.services.ai.azure.com}) resources alike (see the <a
   * href="https://learn.microsoft.com/en-us/azure/foundry/foundry-models/concepts/endpoints">Microsoft
   * Foundry endpoints documentation</a>) by appending {@code /openai/v1} to the URI's <em>path</em>
   * if it doesn't already end with it. Without this, a bare resource endpoint -- exactly what this
   * backend's own template field asks for -- gets routed by the SDK's default {@code
   * AzureUrlPathMode.AUTO} detection as the legacy, deployments-based API instead.
   *
   * <p>A query string or fragment on the endpoint is rejected rather than carried through: the
   * openai-java SDK builds each request URL by appending the service path directly onto the base
   * URL string (see {@code com.openai.core.http.HttpRequest#url()}), so anything after a {@code ?}
   * or {@code #} would leave the {@code /responses} (etc.) path segment stranded inside the query
   * or fragment. A Foundry/Azure OpenAI resource endpoint never legitimately carries either; the
   * backend's dedicated {@code queryParameters} field is the correct place for request query
   * parameters. All trailing slashes are stripped, not just one, so a doubled slash (accidental or
   * already ending in {@code /openai/v1//}) doesn't produce a broken or duplicated suffix.
   *
   * @throws ConnectorInputException if the endpoint is malformed or carries a query/fragment
   */
  static String unifiedEndpoint(String endpoint) {
    final URI uri;
    try {
      uri = new URI(endpoint.strip());
    } catch (URISyntaxException e) {
      throw new ConnectorInputException("Invalid Foundry endpoint: " + endpoint, e);
    }
    if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
      throw new ConnectorInputException(
          "The Foundry endpoint must not contain a query string or fragment: " + endpoint);
    }

    var path = uri.getRawPath() == null ? "" : uri.getRawPath();
    while (path.endsWith("/")) {
      path = path.substring(0, path.length() - 1);
    }
    if (!path.endsWith("/openai/v1")) {
      path = path + "/openai/v1";
    }

    return uri.getScheme() + "://" + uri.getRawAuthority() + path;
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
