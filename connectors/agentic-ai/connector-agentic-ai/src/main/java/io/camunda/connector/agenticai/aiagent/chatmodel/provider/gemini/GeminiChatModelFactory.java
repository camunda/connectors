/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini;

import com.google.genai.Client;
import com.google.genai.types.ClientOptions;
import com.google.genai.types.HttpOptions;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelFactory;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiBackend.GeminiApiBackend;
import io.camunda.connector.agenticai.common.AgenticAiHttpProxySupport;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.net.URI;
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
    // Only one backend variant exists today (google-gemini-api), so this cast is safe.
    final var apiBackend = (GeminiApiBackend) backend;
    final var googleGeminiApi = apiBackend.googleGeminiApi();
    final String endpoint = googleGeminiApi.endpoint();

    final var httpOptionsBuilder = HttpOptions.builder();
    if (endpoint != null) {
      httpOptionsBuilder.baseUrl(endpoint);
    }
    // Stored for introspection/consistency only -- because a customHttpClient is always supplied
    // below, the SDK never reads this value itself; okHttpClientBuilder.callTimeout is what
    // actually enforces the overall timeout.
    if (timeout != null) {
      httpOptionsBuilder.timeout(toGeminiTimeoutMillis(timeout));
    }

    final var okHttpClientBuilder =
        new OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT)
            .readTimeout(Duration.ZERO)
            .writeTimeout(Duration.ZERO);
    if (timeout != null) {
      okHttpClientBuilder.callTimeout(Duration.ofMillis(toGeminiTimeoutMillis(timeout)));
    }

    final String scheme =
        Optional.ofNullable(endpoint).map(e -> URI.create(e).getScheme()).orElse(null);
    httpProxySupport
        .okHttpProxy(scheme != null ? scheme : ProxyConfiguration.SCHEME_HTTPS)
        .ifPresent(proxy -> applyProxy(okHttpClientBuilder, proxy));

    return Client.builder()
        .apiKey(googleGeminiApi.apiKey())
        .httpOptions(httpOptionsBuilder.build())
        .clientOptions(
            ClientOptions.builder().customHttpClient(okHttpClientBuilder.build()).build())
        .build();
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
