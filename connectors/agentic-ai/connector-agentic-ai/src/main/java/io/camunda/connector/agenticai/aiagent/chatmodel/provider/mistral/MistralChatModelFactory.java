/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.mistral;

import static io.camunda.connector.agenticai.aiagent.chatmodel.provider.ChatModelProviderSupport.deriveTimeoutSetting;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.ProxyAuthenticator;
import com.openai.credential.BearerTokenCredential;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsRequestConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsResponseConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsStreamAssembler;
import io.camunda.connector.agenticai.aiagent.model.request.v2.MistralChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.MistralChatModelConfiguration.MistralBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.MistralChatModelConfiguration.MistralBackend.MistralApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.MistralChatModelConfiguration.MistralBackend.MistralCustomBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OAuthClientCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiCustomEndpointAuthentication.ApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiCustomEndpointAuthentication.NoAuthentication;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ChatModelProperties;
import io.camunda.connector.agenticai.common.AgenticAiHttpProxySupport;
import io.camunda.connector.http.client.authentication.OAuthClientCredentialsTokenResolver;
import java.net.URI;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ChatModelFactory} for the native Mistral AI provider's {@code mistral-api} and {@code
 * custom} (Mistral-compatible endpoint, including OAuth 2.0) backends. Mistral's Chat Completions
 * API is OpenAI-shaped, so this factory builds a plain openai-java client (pointed at {@code
 * api.mistral.ai} or the configured custom endpoint) and reuses the OpenAI provider's Chat
 * Completions wire mapper wholesale via {@link MistralChatModel}; only the client construction and
 * the connection-specific configuration are Mistral's own.
 */
public class MistralChatModelFactory implements ChatModelFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger(MistralChatModelFactory.class);

  /**
   * openai-java requires a credential source to build a client at all; this is sent as a
   * placeholder {@code Authorization} header for {@link NoAuthentication} custom backends.
   */
  private static final String NO_AUTH_PLACEHOLDER_API_KEY = "not-required";

  private final ChatModelProperties config;
  private final AgenticAiHttpProxySupport httpProxySupport;
  private final OpenAiCompletionsRequestConverter requestConverter;
  private final OpenAiCompletionsResponseConverter responseConverter;
  private final OpenAiCompletionsStreamAssembler streamAssembler;
  private final OAuthClientCredentialsTokenResolver oAuthClientCredentialsTokenResolver;

  public MistralChatModelFactory(
      ChatModelProperties config,
      AgenticAiHttpProxySupport httpProxySupport,
      OpenAiCompletionsRequestConverter requestConverter,
      OpenAiCompletionsResponseConverter responseConverter,
      OpenAiCompletionsStreamAssembler streamAssembler,
      OAuthClientCredentialsTokenResolver oAuthClientCredentialsTokenResolver) {
    this.config = config;
    this.httpProxySupport = httpProxySupport;
    this.requestConverter = requestConverter;
    this.responseConverter = responseConverter;
    this.streamAssembler = streamAssembler;
    this.oAuthClientCredentialsTokenResolver = oAuthClientCredentialsTokenResolver;
  }

  @Override
  public boolean supports(ChatModelConfiguration configuration) {
    return configuration instanceof MistralChatModelConfiguration;
  }

  @Override
  public ChatModel create(ChatModelConfiguration configuration) {
    final var model = (MistralChatModelConfiguration) configuration;
    final var connection = model.mistral();
    final var timeout =
        deriveTimeoutSetting("Mistral model call", config, connection.timeouts(), LOGGER);

    final var client =
        buildClient(
            connection.backend(), timeout, httpProxySupport, oAuthClientCredentialsTokenResolver);
    return new MistralChatModel(
        client, model, requestConverter, responseConverter, streamAssembler);
  }

  private static OpenAIClient buildClient(
      MistralBackend backend,
      Duration timeout,
      AgenticAiHttpProxySupport httpProxySupport,
      OAuthClientCredentialsTokenResolver oAuthClientCredentialsTokenResolver) {
    final var builder = OpenAIOkHttpClient.builder();

    switch (backend) {
      case MistralApiBackend apiBackend -> applyApiBackend(builder, apiBackend);
      case MistralCustomBackend custom ->
          applyCustomBackend(builder, custom, oAuthClientCredentialsTokenResolver);
    }

    builder.timeout(timeout);

    final String scheme = URI.create(configuredEndpoint(backend)).getScheme();
    httpProxySupport
        .okHttpProxy(scheme)
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
      OpenAIOkHttpClient.Builder builder, MistralApiBackend apiBackend) {
    final var mistral = apiBackend.mistral();
    builder.apiKey(mistral.apiKey());
    builder.baseUrl(configuredEndpoint(apiBackend));
  }

  private static void applyCustomBackend(
      OpenAIOkHttpClient.Builder builder,
      MistralCustomBackend custom,
      OAuthClientCredentialsTokenResolver oAuthClientCredentialsTokenResolver) {
    final var connection = custom.custom();
    builder.baseUrl(connection.endpoint());

    switch (connection.authentication()) {
      case NoAuthentication() -> builder.apiKey(NO_AUTH_PLACEHOLDER_API_KEY);
      case ApiKeyAuthentication apiKeyAuth -> builder.apiKey(apiKeyAuth.apiKey());
      case OAuthClientCredentialsAuthentication oauth ->
          builder.credential(
              BearerTokenCredential.create(
                  () ->
                      oAuthClientCredentialsTokenResolver.resolveAccessToken(
                          oauth.oauthTokenEndpoint(),
                          oauth.clientId(),
                          oauth.clientSecret(),
                          oauth.audience(),
                          oauth.clientAuthentication().oauthConstant(),
                          oauth.scopes())));
    }
  }

  /**
   * The base URL actually configured for this backend, used both as the SDK client's {@code
   * baseUrl} and to pick the proxy scheme: the {@code mistral-api} backend's {@code endpoint} field
   * always carries a default value (see {@link
   * MistralChatModelConfiguration.MistralBackend.MistralApiBackend.MistralApiConnection}), so it is
   * never null in practice, but the template's default is not enforced at the Java type level --
   * falling back here, rather than leaving the SDK's own default (OpenAI's API) in place, keeps a
   * null endpoint from silently sending the Mistral API key to OpenAI. The {@code custom} backend's
   * endpoint is always explicitly set.
   */
  private static String configuredEndpoint(MistralBackend backend) {
    return switch (backend) {
      case MistralApiBackend apiBackend ->
          apiBackend.mistral().endpoint() != null
              ? apiBackend.mistral().endpoint()
              : "https://api.mistral.ai/v1";
      case MistralCustomBackend custom -> custom.custom().endpoint();
    };
  }
}
