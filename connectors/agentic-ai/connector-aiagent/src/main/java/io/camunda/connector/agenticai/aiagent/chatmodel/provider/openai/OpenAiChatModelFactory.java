/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai;

import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.azure.AzureUrlPathMode;
import com.openai.azure.credential.AzureApiKeyCredential;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.Timeout;
import com.openai.core.http.ProxyAuthenticator;
import com.openai.credential.BearerTokenCredential;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.connection.ConnectionResolver;
import io.camunda.connector.agenticai.aiagent.chatmodel.connection.ConnectionSettings;
import io.camunda.connector.agenticai.aiagent.chatmodel.connection.EndpointAuthentication;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.azure.FoundryAuthentication;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiConfiguration.Backend;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.OpenAiApiFamilyStrategy;
import io.camunda.connector.agenticai.aiagent.model.chatmodel.ChatModelConfiguration;
import io.camunda.connector.api.error.ConnectorInputException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;

/**
 * {@link ChatModelFactory} for the native OpenAI provider's {@code openai-api} (API key), {@code
 * foundry} (Microsoft Foundry / Azure OpenAI) and {@code custom} (OpenAI-compatible endpoint)
 * backends, for both the Responses and Chat Completions API families. Client construction is folded
 * in here rather than a separate client-factory class.
 */
public class OpenAiChatModelFactory implements ChatModelFactory {

  /**
   * openai-java requires a credential source to build a client at all, so {@link
   * EndpointAuthentication.None} custom backends configure this as a placeholder API key. The
   * {@code Authorization} header it would otherwise produce is stripped via {@code
   * removeHeaders("Authorization")}, so no header is actually sent on the wire.
   */
  private static final String NO_AUTH_PLACEHOLDER_API_KEY = "not-required";

  private static final String DEFAULT_ENDPOINT = "https://api.openai.com";

  private final ConnectionResolver connectionResolver;
  private final OpenAiApiFamilyStrategy completionsStrategy;
  private final OpenAiApiFamilyStrategy responsesStrategy;

  public OpenAiChatModelFactory(
      ConnectionResolver connectionResolver,
      OpenAiApiFamilyStrategy completionsStrategy,
      OpenAiApiFamilyStrategy responsesStrategy) {
    this.connectionResolver = connectionResolver;
    this.completionsStrategy = completionsStrategy;
    this.responsesStrategy = responsesStrategy;
  }

  @Override
  public boolean supports(ChatModelConfiguration configuration) {
    return configuration instanceof OpenAiConfiguration;
  }

  @Override
  public ChatModel create(ChatModelConfiguration configuration) {
    final var model = (OpenAiConfiguration) configuration;
    final var client = buildClient(model.backend(), model.timeout());
    return new OpenAiChatModel(client, model, strategyFor(model.api()));
  }

  private OpenAiApiFamilyStrategy strategyFor(OpenAiConfiguration.Api api) {
    return switch (api) {
      case OpenAiConfiguration.Api.Completions ignored -> completionsStrategy;
      case OpenAiConfiguration.Api.Responses ignored -> responsesStrategy;
    };
  }

  private OpenAIClient buildClient(Backend backend, Duration timeout) {
    final var settings = connectionResolver.resolve(timeout, backend.endpoint(), DEFAULT_ENDPOINT);
    final var builder = OpenAIOkHttpClient.builder();

    switch (backend) {
      case Backend.Api api -> applyApiBackend(builder, api);
      case Backend.Foundry foundry -> applyFoundryBackend(builder, foundry, timeout);
      case Backend.Custom custom -> applyCustomBackend(builder, custom);
    }

    applyConnectionSettings(builder, settings);
    return builder.build();
  }

  private static void applyConnectionSettings(
      OpenAIOkHttpClient.Builder builder, ConnectionSettings settings) {
    builder.timeout(
        Timeout.builder()
            .connect(settings.connectTimeout())
            .read(settings.timeout())
            .write(settings.timeout())
            .request(settings.timeout())
            .build());
    settings
        .proxy()
        .ifPresent(
            p -> {
              builder.proxy(p.proxy());
              if (p.hasCredentials()) {
                builder.proxyAuthenticator(ProxyAuthenticator.basic(p.username(), p.password()));
              }
            });
  }

  private static void applyApiBackend(OpenAIOkHttpClient.Builder builder, Backend.Api api) {
    builder.apiKey(api.apiKey());

    if (api.organizationId() != null) {
      builder.organization(api.organizationId());
    }
    if (api.projectId() != null) {
      builder.project(api.projectId());
    }
    if (api.endpoint() != null) {
      builder.baseUrl(api.endpoint());
    }
  }

  private void applyCustomBackend(OpenAIOkHttpClient.Builder builder, Backend.Custom custom) {
    builder.baseUrl(custom.endpoint());

    switch (custom.authentication()) {
      case EndpointAuthentication.None ignored ->
          builder.apiKey(NO_AUTH_PLACEHOLDER_API_KEY).removeHeaders("Authorization");
      case EndpointAuthentication.ApiKey apiKey -> builder.apiKey(apiKey.apiKey());
      case EndpointAuthentication.OAuthClientCredentials oauth ->
          builder.credential(BearerTokenCredential.create(connectionResolver.bearerToken(oauth)));
    }
  }

  /**
   * Applies the {@code foundry} backend: base URL (normalized onto the unified OpenAI/v1 API
   * surface, see {@link #unifiedEndpoint(String)}), an optional {@code apiVersion} pin, and the
   * {@link com.openai.credential.Credential} built here for the configured authentication variant.
   * {@code apiVersion} is only wired when explicitly set: the unified surface uses implicit
   * versioning, so it's an escape hatch for pinning a specific version rather than something every
   * request needs.
   *
   * <p>{@code azureUrlPathMode} is forced to {@link AzureUrlPathMode#UNIFIED} rather than left on
   * the SDK's default {@code AUTO} host-sniffing: the endpoint is already unconditionally
   * normalized onto {@code /openai/v1} above, and {@code AUTO}'s host allowlist doesn't recognize
   * Azure Government ({@code *.azure.us}) hosts as Azure at all, which would silently classify
   * those requests as non-Azure and skip the {@code apiVersion} escape hatch (and other
   * Azure-specific handling) regardless of path.
   */
  private void applyFoundryBackend(
      OpenAIOkHttpClient.Builder builder, Backend.Foundry foundry, Duration timeout) {
    builder.baseUrl(unifiedEndpoint(foundry.endpoint()));
    builder.azureUrlPathMode(AzureUrlPathMode.UNIFIED);

    if (foundry.apiVersion() != null) {
      builder.azureServiceVersion(AzureOpenAIServiceVersion.fromString(foundry.apiVersion()));
    }

    switch (foundry.authentication()) {
      case FoundryAuthentication.ApiKey apiKey ->
          builder.credential(AzureApiKeyCredential.create(apiKey.apiKey()));
      case FoundryAuthentication.ClientCredentials clientCredentials ->
          builder.credential(
              BearerTokenCredential.create(
                  connectionResolver.bearerToken(clientCredentials, timeout)));
      case FoundryAuthentication.ManagedIdentity managedIdentity ->
          builder.credential(
              BearerTokenCredential.create(
                  connectionResolver.bearerToken(managedIdentity, timeout)));
    }
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
}
