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
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelFactory;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiBackend.GeminiApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiBackend.GeminiVertexAiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiBackend.GoogleVertexAiAuthentication.ServiceAccountCredentialsAuthentication;
import io.camunda.connector.agenticai.common.AgenticAiHttpProxySupport;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.http.client.proxy.NonProxyHosts;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import org.jspecify.annotations.Nullable;

public class GeminiChatModelFactory implements ChatModelFactory {

  /**
   * {@code HttpOptions.timeout} only accepts an {@code Integer} millisecond value, while the
   * connector accepts any positive {@link Duration}.
   */
  private static final Duration MAX_GEMINI_TIMEOUT = Duration.ofMillis(Integer.MAX_VALUE);

  /**
   * The SDK's own default {@link OkHttpClient} (built when no {@code customHttpClient} is supplied)
   * sets {@code connectTimeout}/{@code readTimeout}/{@code writeTimeout} to zero (unbounded) and
   * relies solely on {@link HttpOptions#timeout} as an overall {@code callTimeout} -- a hung TCP
   * connect would otherwise consume the whole, often much longer, configured request budget before
   * failing. Bounding connect separately, at OkHttp's own upstream default, lets a genuinely
   * unreachable host fail fast regardless of how generous the configured overall timeout is.
   *
   * <p>Supplying a {@code customHttpClient} at all changes {@code ApiClient#createHttpClient}'s
   * behavior beyond just the timeouts: on that branch, the SDK skips applying {@code
   * HttpOptions#timeout} and {@code ClientOptions#proxyOptions} entirely and trusts the supplied
   * client as-is. {@link #buildClient} therefore applies the overall timeout and proxy directly on
   * this custom client instead of through those SDK options.
   *
   * <p>A bare {@code new OkHttpClient.Builder()}, unlike the SDK's own default client, does not
   * default {@code readTimeout}/{@code writeTimeout} to zero -- it defaults them to OkHttp's own
   * stock 10-second value. Left alone, a Gemini SSE stream with a longer gap between chunks (e.g.
   * during an extended thinking phase) would abort mid-stream regardless of how generous the
   * configured overall timeout is, so {@link #buildClient} explicitly zeroes both, leaving only
   * connect bounded and {@code callTimeout} governing the overall budget.
   */
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

  private static final String GOOGLE_CLOUD_PLATFORM_SCOPE =
      "https://www.googleapis.com/auth/cloud-platform";

  private static final String DEFAULT_GEMINI_API_BASE_URL =
      "https://generativelanguage.googleapis.com";

  private final AgenticAiHttpProxySupport httpProxySupport;
  private final GeminiContentRequestConverter requestConverter;
  private final GeminiContentResponseConverter responseConverter;

  public GeminiChatModelFactory(
      AgenticAiHttpProxySupport httpProxySupport,
      GeminiContentRequestConverter requestConverter,
      GeminiContentResponseConverter responseConverter) {
    this.httpProxySupport = httpProxySupport;
    this.requestConverter = requestConverter;
    this.responseConverter = responseConverter;
  }

  @Override
  public boolean supports(ChatModelConfiguration configuration) {
    return configuration instanceof GeminiChatModelConfiguration;
  }

  @Override
  public ChatModel create(ChatModelConfiguration configuration) {
    final var model = (GeminiChatModelConfiguration) configuration;
    final var connection = model.googleGemini();
    final var configuredTimeout =
        connection.timeouts() != null ? connection.timeouts().timeout() : null;
    // a non-positive configured timeout (e.g. PT0S, or a negative FEEL result) falls back to
    // the SDK default rather than being passed through - see toGeminiTimeoutMillis, which
    // would otherwise clamp it to an unusable 1ms call timeout
    final var timeout =
        configuredTimeout != null && configuredTimeout.isPositive() ? configuredTimeout : null;

    final var client = buildClient(connection.backend(), timeout, httpProxySupport);
    return new GeminiChatModel(client, model, requestConverter, responseConverter);
  }

  private static Client buildClient(
      GeminiBackend backend,
      @Nullable Duration timeout,
      AgenticAiHttpProxySupport httpProxySupport) {
    // the configured endpoint override, if any - only ever set for e2e tests (see the field's
    // javadoc); when unset, the SDK derives its own production base URL for the backend at build
    // time (which, for google-vertex-ai, is not always byte-identical to
    // AgenticAiHttpProxySupport.defaultGoogleGenAiBaseUrl - e.g. the "us"/"eu" multi-region
    // hosts), so it must not be pinned here.
    final String endpointOverride = configuredEndpoint(backend);

    final var httpOptionsBuilder = HttpOptions.builder();
    if (endpointOverride != null) {
      httpOptionsBuilder.baseUrl(endpointOverride);
    }
    // Stored for introspection/consistency only -- because a customHttpClient is always supplied
    // below, the SDK never reads this value itself; okHttpClientBuilder.callTimeout is what
    // actually enforces the overall timeout.
    if (timeout != null) {
      httpOptionsBuilder.timeout(toGeminiTimeoutMillis(timeout));
    }

    final var clientBuilder = Client.builder().httpOptions(httpOptionsBuilder.build());

    switch (backend) {
      case GeminiApiBackend apiBackend ->
          clientBuilder.apiKey(apiBackend.googleGeminiApi().apiKey());
      case GeminiVertexAiBackend vertexAiBackend ->
          applyVertexAiBackend(clientBuilder, vertexAiBackend);
    }

    final var okHttpClientBuilder =
        new OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT)
            .readTimeout(Duration.ZERO)
            .writeTimeout(Duration.ZERO);
    if (timeout != null) {
      okHttpClientBuilder.callTimeout(Duration.ofMillis(toGeminiTimeoutMillis(timeout)));
    }

    // the proxy scheme is derived from the endpoint override, if any; when unset, the proxy
    // configuration's default scheme (https) is used
    final String scheme =
        Optional.ofNullable(endpointOverride).map(url -> URI.create(url).getScheme()).orElse(null);
    final String targetHost = resolveTargetHost(backend, endpointOverride);
    if (!NonProxyHosts.isNonProxyHost(targetHost)) {
      httpProxySupport
          .okHttpProxy(scheme != null ? scheme : ProxyConfiguration.SCHEME_HTTPS)
          .ifPresent(proxy -> applyProxy(okHttpClientBuilder, proxy));
    }

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

  private static @Nullable String configuredEndpoint(GeminiBackend backend) {
    return switch (backend) {
      case GeminiApiBackend apiBackend -> apiBackend.googleGeminiApi().endpoint();
      case GeminiVertexAiBackend vertexAiBackend -> vertexAiBackend.googleVertexAi().endpoint();
    };
  }

  /**
   * Resolves the host the SDK will actually target, so the proxy can be skipped when it matches a
   * configured non-proxy host pattern. {@code com.google.genai.types.ProxyOptions} -- the SDK's own
   * proxy mechanism, which does understand a bypass list, see the v1 Vertex AI provider's {@code
   * ChatModelHttpProxySupport#createGoogleGenAiProxyOptions} -- is never used here, because
   * supplying a {@code customHttpClient} (required for the timeout/streaming behavior documented on
   * {@link #CONNECT_TIMEOUT}) makes the SDK ignore it entirely, so the same bypass has to be
   * reimplemented against the raw OkHttp client instead.
   */
  private static String resolveTargetHost(
      GeminiBackend backend, @Nullable String endpointOverride) {
    if (endpointOverride != null) {
      return URI.create(endpointOverride).getHost();
    }
    return switch (backend) {
      case GeminiApiBackend ignored -> URI.create(DEFAULT_GEMINI_API_BASE_URL).getHost();
      case GeminiVertexAiBackend vertexAiBackend ->
          URI.create(
                  AgenticAiHttpProxySupport.defaultGoogleGenAiBaseUrl(
                      vertexAiBackend.googleVertexAi().region()))
              .getHost();
    };
  }

  private static void applyVertexAiBackend(
      Client.Builder clientBuilder, GeminiVertexAiBackend vertexAiBackend) {
    final var googleVertexAi = vertexAiBackend.googleVertexAi();
    clientBuilder
        .vertexAI(true)
        .project(googleVertexAi.projectId())
        .location(googleVertexAi.region());

    // application default credentials are left unset here - the SDK resolves them itself when
    // the client is built
    if (googleVertexAi.authentication() instanceof ServiceAccountCredentialsAuthentication sac) {
      clientBuilder.credentials(createServiceAccountCredentials(sac));
    }
  }

  private static GoogleCredentials createServiceAccountCredentials(
      ServiceAccountCredentialsAuthentication sac) {
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
   * Resolution and logging are shared with the Anthropic/OpenAI providers via {@link
   * AgenticAiHttpProxySupport#okHttpProxy}; only the authenticator differs, since those providers'
   * SDKs accept a {@code Proxy} directly while Gemini's raw {@link OkHttpClient.Builder} needs its
   * own {@link okhttp3.Authenticator}.
   */
  private static void applyProxy(
      OkHttpClient.Builder builder, AgenticAiHttpProxySupport.OkHttpProxy proxy) {
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
