/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.mistral;

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
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.mistral.MistralConfiguration.Backend;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsRequestConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsResponseConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsStreamAssembler;
import io.camunda.connector.agenticai.aiagent.model.chatmodel.ChatModelConfiguration;
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

  private final ConnectionResolver connectionResolver;
  private final OpenAiCompletionsRequestConverter requestConverter;
  private final OpenAiCompletionsResponseConverter responseConverter;
  private final OpenAiCompletionsStreamAssembler streamAssembler;

  public MistralChatModelFactory(
      ConnectionResolver connectionResolver,
      OpenAiCompletionsRequestConverter requestConverter,
      OpenAiCompletionsResponseConverter responseConverter,
      OpenAiCompletionsStreamAssembler streamAssembler) {
    this.connectionResolver = connectionResolver;
    this.requestConverter = requestConverter;
    this.responseConverter = responseConverter;
    this.streamAssembler = streamAssembler;
  }

  @Override
  public boolean supports(ChatModelConfiguration configuration) {
    return configuration instanceof MistralConfiguration;
  }

  @Override
  public ChatModel create(ChatModelConfiguration configuration) {
    final var model = (MistralConfiguration) configuration;
    final var client = buildClient(model.backend(), model.timeout());
    return new MistralChatModel(
        client, model, requestConverter, responseConverter, streamAssembler);
  }

  private OpenAIClient buildClient(Backend backend, Duration timeout) {
    final var settings =
        connectionResolver.resolve(timeout, backend.endpoint(), backend.endpoint());
    final var builder = OpenAIOkHttpClient.builder();

    switch (backend) {
      case Backend.Api api -> applyApiBackend(builder, api);
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
    builder.baseUrl(api.endpoint());
  }

  private void applyCustomBackend(OpenAIOkHttpClient.Builder builder, Backend.Custom custom) {
    builder.baseUrl(custom.endpoint());

    switch (custom.authentication()) {
      case EndpointAuthentication.None() ->
          builder.apiKey(NO_AUTH_PLACEHOLDER_API_KEY).removeHeaders("Authorization");
      case EndpointAuthentication.ApiKey apiKeyAuth -> builder.apiKey(apiKeyAuth.apiKey());
      case EndpointAuthentication.OAuthClientCredentials oauth ->
          builder.credential(BearerTokenCredential.create(connectionResolver.bearerToken(oauth)));
    }
  }
}
