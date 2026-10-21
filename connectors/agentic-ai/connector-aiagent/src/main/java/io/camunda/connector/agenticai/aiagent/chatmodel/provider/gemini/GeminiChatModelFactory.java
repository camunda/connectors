/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.genai.Client;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.ClientOptions;
import com.google.genai.types.HttpOptions;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.connection.ConnectionResolver;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini.GeminiConfiguration.Authentication.ServiceAccountKey;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini.GeminiConfiguration.Backend;
import io.camunda.connector.agenticai.aiagent.model.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.common.http.AgenticAiHttpProxySupport.OkHttpProxy;
import io.camunda.connector.api.error.ConnectorInputException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import org.jspecify.annotations.Nullable;

public class GeminiChatModelFactory implements ChatModelFactory {

  /**
   * {@code HttpOptions.timeout} only accepts an {@code Integer} millisecond value, while the
   * connector accepts any positive {@link Duration}.
   */
  private static final Duration MAX_GEMINI_TIMEOUT = Duration.ofMillis(Integer.MAX_VALUE);

  private static final String GOOGLE_CLOUD_PLATFORM_SCOPE =
      "https://www.googleapis.com/auth/cloud-platform";

  private static final String DEFAULT_GEMINI_API_BASE_URL =
      "https://generativelanguage.googleapis.com";

  private final ConnectionResolver connectionResolver;
  private final GeminiContentRequestConverter requestConverter;
  private final GeminiContentResponseConverter responseConverter;

  public GeminiChatModelFactory(
      ConnectionResolver connectionResolver,
      GeminiContentRequestConverter requestConverter,
      GeminiContentResponseConverter responseConverter) {
    this.connectionResolver = connectionResolver;
    this.requestConverter = requestConverter;
    this.responseConverter = responseConverter;
  }

  @Override
  public boolean supports(ChatModelConfiguration configuration) {
    return configuration instanceof GeminiConfiguration;
  }

  @Override
  public ChatModel create(ChatModelConfiguration configuration) {
    final var model = (GeminiConfiguration) configuration;
    final var client = buildClient(model.backend(), model.timeout());
    return new GeminiChatModel(client, model, requestConverter, responseConverter);
  }

  /**
   * Builds the client around a custom {@link OkHttpClient}: with a {@code customHttpClient}, the
   * SDK skips applying {@link HttpOptions#timeout} and {@code ClientOptions#proxyOptions} and
   * trusts the supplied client as-is, so the connect timeout, the overall call timeout and the
   * proxy of the resolved connection settings are applied on this client directly.
   *
   * <p>A bare {@code OkHttpClient.Builder} defaults {@code readTimeout} and {@code writeTimeout} to
   * ten seconds. Left alone, a Gemini SSE stream with a longer gap between chunks (for example
   * during an extended thinking phase) would abort mid-stream however generous the configured
   * timeout is, so both are zeroed and only the connect and the overall call timeout bound the
   * call.
   */
  private Client buildClient(Backend backend, Duration timeout) {
    final String endpointOverride = configuredEndpoint(backend);
    final var settings =
        connectionResolver.resolve(timeout, endpointOverride, defaultEndpoint(backend));

    final var httpOptionsBuilder = HttpOptions.builder();
    if (endpointOverride != null) {
      httpOptionsBuilder.baseUrl(endpointOverride);
    }
    httpOptionsBuilder.timeout(toGeminiTimeoutMillis(timeout));

    final var clientBuilder = Client.builder().httpOptions(httpOptionsBuilder.build());

    switch (backend) {
      case Backend.Api api -> clientBuilder.apiKey(api.apiKey());
      case Backend.VertexAi vertexAi -> applyVertexAiBackend(clientBuilder, vertexAi);
    }

    final var okHttpClientBuilder =
        new OkHttpClient.Builder()
            .connectTimeout(settings.connectTimeout())
            .readTimeout(Duration.ZERO)
            .writeTimeout(Duration.ZERO);
    okHttpClientBuilder.callTimeout(Duration.ofMillis(toGeminiTimeoutMillis(timeout)));

    settings.proxy().ifPresent(proxy -> applyProxy(okHttpClientBuilder, proxy));

    clientBuilder.clientOptions(
        ClientOptions.builder().customHttpClient(okHttpClientBuilder.build()).build());

    try {
      // for the google-vertex-ai backend with application default credentials, the credentials
      // are resolved here, eagerly, by the SDK
      return clientBuilder.build();
    } catch (GenAiIOException | IllegalArgumentException e) {
      throw new ConnectorInputException("Failed to create Google GenAI client", e);
    }
  }

  private static @Nullable String configuredEndpoint(Backend backend) {
    return switch (backend) {
      case Backend.Api api -> api.endpoint();
      case Backend.VertexAi vertexAi -> vertexAi.endpoint();
    };
  }

  /** The endpoint which the SDK connects to when the configuration does not set one. */
  private static String defaultEndpoint(Backend backend) {
    return switch (backend) {
      case Backend.Api ignored -> DEFAULT_GEMINI_API_BASE_URL;
      case Backend.VertexAi vertexAi ->
          GeminiEndpoints.defaultGoogleGenAiBaseUrl(vertexAi.region());
    };
  }

  private static void applyVertexAiBackend(
      Client.Builder clientBuilder, Backend.VertexAi vertexAi) {
    clientBuilder.vertexAI(true).project(vertexAi.projectId()).location(vertexAi.region());

    // application default credentials are left unset here - the SDK resolves them itself when
    // the client is built
    if (vertexAi.authentication() instanceof ServiceAccountKey sac) {
      clientBuilder.credentials(createServiceAccountCredentials(sac));
    }
  }

  private static GoogleCredentials createServiceAccountCredentials(ServiceAccountKey sac) {
    try {
      // Credentials read from a key file carry no scopes. google-genai only scopes the
      // application default credentials it resolves itself and passes these through verbatim,
      // so without this the token request fails with invalid_scope.
      return ServiceAccountCredentials.fromStream(
              new ByteArrayInputStream(sac.jsonKey().getBytes(StandardCharsets.UTF_8)))
          .createScoped(GOOGLE_CLOUD_PLATFORM_SCOPE);
    } catch (IOException e) {
      throw new ConnectorInputException(
          "Authentication failed for provided service account credentials", e);
    }
  }

  /**
   * The proxy is resolved with the connection settings; only the authenticator differs from the
   * other OkHttp based providers, since their SDKs accept a {@code Proxy} directly while Gemini's
   * raw {@link OkHttpClient.Builder} needs its own {@link okhttp3.Authenticator}.
   */
  private static void applyProxy(OkHttpClient.Builder builder, OkHttpProxy proxy) {
    builder.proxy(proxy.proxy());

    if (proxy.hasCredentials()) {
      final String credential = Credentials.basic(proxy.username(), proxy.password());
      // Only answers a proxy's 407 challenge once per request: if the prior attempt already
      // carried this header, OkHttp calls the authenticator again because the proxy rejected it a
      // second time, and returning the same credential again would retry forever.
      builder.proxyAuthenticator(
          (route, response) ->
              response.request().header("Proxy-Authorization") != null
                  ? null
                  : response
                      .request()
                      .newBuilder()
                      .header("Proxy-Authorization", credential)
                      .build());
    }
  }

  /**
   * Values above {@code Integer.MAX_VALUE} ms (~24.8 days) would silently overflow on a raw cast,
   * so reject them with a clear input error instead. A positive sub-millisecond timeout would
   * otherwise truncate to 0, which is clamped to the shortest possible timeout (1ms) instead.
   */
  private static int toGeminiTimeoutMillis(Duration timeout) {
    if (timeout.compareTo(MAX_GEMINI_TIMEOUT) > 0) {
      throw new ConnectorInputException(
          "Configured timeout of %s exceeds the maximum supported by the Google GenAI SDK (%dms)"
              .formatted(timeout, Integer.MAX_VALUE));
    }

    return (int) Math.max(1, timeout.toMillis());
  }
}
