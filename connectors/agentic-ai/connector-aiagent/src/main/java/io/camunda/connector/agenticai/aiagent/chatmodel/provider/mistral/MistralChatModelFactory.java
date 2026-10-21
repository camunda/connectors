/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.mistral;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.ProxyAuthenticator;
import com.openai.credential.BearerTokenCredential;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.connection.EndpointAuthentication;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.mistral.MistralConfiguration.Backend;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsRequestConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsResponseConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsStreamAssembler;
import io.camunda.connector.agenticai.aiagent.model.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.common.http.AgenticAiHttpProxySupport;
import io.camunda.connector.http.client.authentication.OAuthClientCredentialsTokenResolver;
import java.net.URI;
import java.time.Duration;

/**
 * {@link ChatModelFactory} for the native Mistral AI provider's {@code mistral-api} and {@code
 * custom} (Mistral-compatible endpoint, including OAuth 2.0) backends. Mistral's Chat Completions
 * API is OpenAI-shaped, so this factory builds a plain openai-java client (pointed at {@code
 * api.mistral.ai} or the configured custom endpoint) and reuses the OpenAI provider's Chat
 * Completions wire mapper wholesale via {@link MistralChatModel}; only the client construction and
 * the connection-specific configuration are Mistral's own.
 */
public class MistralChatModelFactory implements ChatModelFactory {

  /**
   * openai-java requires a credential source to build a client at all, so {@link
   * EndpointAuthentication.None} custom backends configure this as a placeholder API key. The
   * {@code Authorization} header it would otherwise produce is stripped via {@code
   * removeHeaders("Authorization")}, so no header is actually sent on the wire.
   */
  private static final String NO_AUTH_PLACEHOLDER_API_KEY = "not-required";

  private final AgenticAiHttpProxySupport httpProxySupport;
  private final OpenAiCompletionsRequestConverter requestConverter;
  private final OpenAiCompletionsResponseConverter responseConverter;
  private final OpenAiCompletionsStreamAssembler streamAssembler;
  private final OAuthClientCredentialsTokenResolver oAuthClientCredentialsTokenResolver;

  public MistralChatModelFactory(
      AgenticAiHttpProxySupport httpProxySupport,
      OpenAiCompletionsRequestConverter requestConverter,
      OpenAiCompletionsResponseConverter responseConverter,
      OpenAiCompletionsStreamAssembler streamAssembler,
      OAuthClientCredentialsTokenResolver oAuthClientCredentialsTokenResolver) {
    this.httpProxySupport = httpProxySupport;
    this.requestConverter = requestConverter;
    this.responseConverter = responseConverter;
    this.streamAssembler = streamAssembler;
    this.oAuthClientCredentialsTokenResolver = oAuthClientCredentialsTokenResolver;
  }

  @Override
  public boolean supports(ChatModelConfiguration configuration) {
    return configuration instanceof MistralConfiguration;
  }

  @Override
  public ChatModel create(ChatModelConfiguration configuration) {
    final var model = (MistralConfiguration) configuration;
    final var client =
        buildClient(
            model.backend(),
            model.timeout(),
            httpProxySupport,
            oAuthClientCredentialsTokenResolver);
    return new MistralChatModel(
        client, model, requestConverter, responseConverter, streamAssembler);
  }

  private static OpenAIClient buildClient(
      Backend backend,
      Duration timeout,
      AgenticAiHttpProxySupport httpProxySupport,
      OAuthClientCredentialsTokenResolver oAuthClientCredentialsTokenResolver) {
    final var builder = OpenAIOkHttpClient.builder();

    switch (backend) {
      case Backend.Api api -> applyApiBackend(builder, api);
      case Backend.Custom custom ->
          applyCustomBackend(builder, custom, oAuthClientCredentialsTokenResolver);
    }

    builder.timeout(timeout);

    final String scheme = URI.create(endpoint(backend)).getScheme();
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

  private static void applyApiBackend(OpenAIOkHttpClient.Builder builder, Backend.Api api) {
    builder.apiKey(api.apiKey());
    builder.baseUrl(api.endpoint());
  }

  private static void applyCustomBackend(
      OpenAIOkHttpClient.Builder builder,
      Backend.Custom custom,
      OAuthClientCredentialsTokenResolver oAuthClientCredentialsTokenResolver) {
    builder.baseUrl(custom.endpoint());

    switch (custom.authentication()) {
      case EndpointAuthentication.None() ->
          builder.apiKey(NO_AUTH_PLACEHOLDER_API_KEY).removeHeaders("Authorization");
      case EndpointAuthentication.ApiKey apiKeyAuth -> builder.apiKey(apiKeyAuth.apiKey());
      case EndpointAuthentication.OAuthClientCredentials oauth ->
          builder.credential(
              BearerTokenCredential.create(
                  () ->
                      oAuthClientCredentialsTokenResolver.resolveAccessToken(
                          oauth.tokenEndpoint(),
                          oauth.clientId(),
                          oauth.clientSecret(),
                          oauth.audience(),
                          oauth.clientAuthentication().oauthConstant(),
                          oauth.scopes())));
    }
  }

  private static String endpoint(Backend backend) {
    return switch (backend) {
      case Backend.Api api -> api.endpoint();
      case Backend.Custom custom -> custom.endpoint();
    };
  }
}
