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
import com.google.genai.types.ProxyOptions;
import com.google.genai.types.ProxyType;
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
import org.jspecify.annotations.Nullable;

public class GeminiChatModelFactory implements ChatModelFactory {

  /**
   * {@code HttpOptions.timeout} only accepts an {@code Integer} millisecond value, while the
   * connector accepts any positive {@link Duration}.
   */
  private static final Duration MAX_GEMINI_TIMEOUT = Duration.ofMillis(Integer.MAX_VALUE);

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
    if (timeout != null) {
      httpOptionsBuilder.timeout(toGeminiTimeoutMillis(timeout));
    }

    final var clientBuilder =
        Client.builder().apiKey(googleGeminiApi.apiKey()).httpOptions(httpOptionsBuilder.build());

    final String scheme =
        Optional.ofNullable(endpoint).map(e -> URI.create(e).getScheme()).orElse(null);
    httpProxySupport
        .getProxyConfiguration()
        .getProxyDetails(scheme != null ? scheme : ProxyConfiguration.SCHEME_HTTPS)
        .ifPresent(
            proxyDetails ->
                clientBuilder.clientOptions(
                    ClientOptions.builder().proxyOptions(toProxyOptions(proxyDetails)).build()));

    return clientBuilder.build();
  }

  private static ProxyOptions toProxyOptions(ProxyConfiguration.ProxyDetails proxyDetails) {
    final var builder =
        ProxyOptions.builder()
            .type(ProxyType.Known.HTTP)
            .host(proxyDetails.host())
            .port(proxyDetails.port());

    if (proxyDetails.hasCredentials()) {
      builder.username(proxyDetails.user()).password(proxyDetails.password());
    }

    return builder.build();
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
