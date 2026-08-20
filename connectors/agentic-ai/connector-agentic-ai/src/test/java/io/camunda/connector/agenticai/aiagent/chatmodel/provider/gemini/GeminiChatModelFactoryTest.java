/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.ApiClient;
import com.google.genai.Client;
import com.google.genai.types.ClientOptions;
import com.google.genai.types.HttpOptions;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.shared.TimeoutConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.CustomProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiBackend.GeminiApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiBackend.GeminiApiBackend.GoogleGeminiApi;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiModel;
import io.camunda.connector.agenticai.common.AgenticAiHttpProxySupport;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import okhttp3.Authenticator;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GeminiChatModelFactoryTest {

  private static final String MODEL_ID = "gemini-3-pro-preview";
  private static final String API_KEY = "test-api-key";

  @Mock private AgenticAiHttpProxySupport httpProxySupport;
  @Mock private ProxyConfiguration proxyConfiguration;

  private GeminiChatModelFactory factory;

  @BeforeEach
  void setUp() {
    factory =
        new GeminiChatModelFactory(
            httpProxySupport,
            new GeminiContentRequestConverter(new GeminiContentConverter(new ObjectMapper())),
            new GeminiContentResponseConverter());
  }

  @Test
  void supportsGeminiV2Config() {
    assertThat(factory.supports(apiConfig(null, null))).isTrue();
  }

  @Test
  void doesNotSupportOtherProviderConfiguration() {
    final ChatModelConfiguration config =
        new CustomProviderConfiguration("some-custom-provider", MODEL_ID, Map.of());

    assertThat(factory.supports(config)).isFalse();
  }

  @Test
  void createBuildsWorkingChatModelWithDefaultEndpoint() {
    noProxyConfigured();

    final ChatModel model = factory.create(apiConfig(null, null));

    assertThat(model).isNotNull().isInstanceOf(GeminiChatModel.class);

    final HttpOptions httpOptions = httpOptionsOf(model);
    // the SDK fills in its production default base URL when no override is configured
    assertThat(httpOptions.baseUrl()).contains("https://generativelanguage.googleapis.com");
    assertThat(httpOptions.timeout()).isEmpty();
    assertThat(clientOf(model).apiKey()).isEqualTo(API_KEY);

    verify(proxyConfiguration).getProxyDetails(ProxyConfiguration.SCHEME_HTTPS);

    model.close();
  }

  @Test
  void createBuildsWorkingChatModelWithHiddenEndpoint() {
    final String endpoint = "http://localhost:8080";
    noProxyConfigured();

    final ChatModel model = factory.create(apiConfig(endpoint, null));

    assertThat(httpOptionsOf(model).baseUrl()).contains(endpoint);

    verify(proxyConfiguration).getProxyDetails(ProxyConfiguration.SCHEME_HTTP);

    model.close();
  }

  @Test
  void createSetsConfiguredTimeout() {
    noProxyConfigured();

    final ChatModel model = factory.create(apiConfig(null, Duration.ofSeconds(30)));

    assertThat(httpOptionsOf(model).timeout()).contains(30_000);

    model.close();
  }

  @Test
  void createClampsSubMillisecondTimeoutToOneMillisecond() {
    noProxyConfigured();

    final ChatModel model = factory.create(apiConfig(null, Duration.ofNanos(500)));

    assertThat(httpOptionsOf(model).timeout()).contains(1);

    model.close();
  }

  @Test
  void createIgnoresNonPositiveConfiguredTimeout() {
    noProxyConfigured();

    final ChatModel zeroTimeoutModel = factory.create(apiConfig(null, Duration.ZERO));
    assertThat(httpOptionsOf(zeroTimeoutModel).timeout()).isEmpty();
    zeroTimeoutModel.close();

    final ChatModel negativeTimeoutModel = factory.create(apiConfig(null, Duration.ofSeconds(-1)));
    assertThat(httpOptionsOf(negativeTimeoutModel).timeout()).isEmpty();
    negativeTimeoutModel.close();
  }

  @Test
  void createThrowsWhenTimeoutExceedsMaximumSupportedMillis() {
    final GeminiChatModelConfiguration config =
        apiConfig(null, Duration.ofMillis(Integer.MAX_VALUE).plusMillis(1));

    assertThatThrownBy(() -> factory.create(config)).isInstanceOf(ConnectorInputException.class);
  }

  @Test
  void createAppliesProxyDirectlyToTheCustomHttpClient() {
    // Applied directly to the OkHttp client we own, not via ClientOptions.proxyOptions() -
    // supplying a customHttpClient (required for the connect timeout) makes the SDK skip applying
    // that option entirely, see GeminiChatModelFactory#buildClient's javadoc on CONNECT_TIMEOUT.
    // GeminiChatModelFactoryClientTest exercises the real wire behavior (address + credentials)
    // end to end; this only checks that the client we hand the SDK is configured at all.
    when(httpProxySupport.getProxyConfiguration()).thenReturn(proxyConfiguration);
    when(proxyConfiguration.getProxyDetails(any()))
        .thenReturn(
            Optional.of(
                new ProxyConfiguration.ProxyDetails(
                    "https", "proxy.example.com", 8080, "proxy-user", "proxy-pass")));

    final ChatModel model = factory.create(apiConfig(null, null));

    final var customHttpClient = clientOptionsOf(model).orElseThrow().customHttpClient();
    assertThat(customHttpClient).isPresent();
    final var proxyAddress = (InetSocketAddress) customHttpClient.get().proxy().address();
    assertThat(proxyAddress.getHostString()).isEqualTo("proxy.example.com");
    assertThat(proxyAddress.getPort()).isEqualTo(8080);
    assertThat(customHttpClient.get().proxyAuthenticator()).isNotEqualTo(Authenticator.NONE);

    verify(proxyConfiguration).getProxyDetails(ProxyConfiguration.SCHEME_HTTPS);

    model.close();
  }

  @Test
  void createOmitsProxyAuthenticatorWhenProxyHasNoCredentials() {
    when(httpProxySupport.getProxyConfiguration()).thenReturn(proxyConfiguration);
    when(proxyConfiguration.getProxyDetails(any()))
        .thenReturn(
            Optional.of(
                new ProxyConfiguration.ProxyDetails(
                    "https", "proxy.example.com", 8080, null, null)));

    final ChatModel model = factory.create(apiConfig(null, null));

    final var customHttpClient = clientOptionsOf(model).orElseThrow().customHttpClient();
    assertThat(customHttpClient.get().proxyAuthenticator()).isEqualTo(Authenticator.NONE);

    model.close();
  }

  @Test
  void createSetsConnectTimeoutIndependentlyOfOverallTimeout() {
    noProxyConfigured();

    // The overall (callTimeout) timeout is deliberately left unset here: connect timeout must be
    // wired even when no overall timeout is configured at all.
    final ChatModel model = factory.create(apiConfig(null, null));

    final Optional<ClientOptions> clientOptions = clientOptionsOf(model);
    assertThat(clientOptions).isPresent();
    final var customHttpClient = clientOptions.get().customHttpClient();
    assertThat(customHttpClient).isPresent();
    assertThat(customHttpClient.get().connectTimeoutMillis()).isEqualTo(10_000);

    model.close();
  }

  @Test
  void createLeavesReadAndWriteTimeoutsUnboundedEvenWithAnOverallTimeoutConfigured() {
    noProxyConfigured();

    // A bare OkHttpClient.Builder() defaults readTimeout/writeTimeout to OkHttp's own stock
    // 10-second value (unlike the SDK's own default client, which zeroes them) - left alone, a
    // Gemini SSE stream with a longer gap between chunks would abort mid-stream regardless of how
    // generous this configured overall timeout is.
    final ChatModel model = factory.create(apiConfig(null, Duration.ofMinutes(5)));

    final var customHttpClient = clientOptionsOf(model).orElseThrow().customHttpClient();
    assertThat(customHttpClient).isPresent();
    assertThat(customHttpClient.get().readTimeoutMillis()).isZero();
    assertThat(customHttpClient.get().writeTimeoutMillis()).isZero();

    model.close();
  }

  private void noProxyConfigured() {
    when(httpProxySupport.getProxyConfiguration()).thenReturn(proxyConfiguration);
    when(proxyConfiguration.getProxyDetails(any())).thenReturn(Optional.empty());
  }

  private static Client clientOf(ChatModel model) {
    return (Client) ReflectionTestUtils.getField(model, "client");
  }

  private static ApiClient apiClientOf(ChatModel model) {
    return (ApiClient) ReflectionTestUtils.getField(clientOf(model), "apiClient");
  }

  private static HttpOptions httpOptionsOf(ChatModel model) {
    return apiClientOf(model).httpOptions();
  }

  @SuppressWarnings("unchecked")
  private static Optional<ClientOptions> clientOptionsOf(ChatModel model) {
    return (Optional<ClientOptions>)
        ReflectionTestUtils.getField(apiClientOf(model), "clientOptions");
  }

  private static GeminiChatModelConfiguration apiConfig(
      @Nullable String endpoint, @Nullable Duration timeout) {
    return new GeminiChatModelConfiguration(
        new GeminiConnection(
            new GeminiApiBackend(new GoogleGeminiApi(API_KEY, endpoint)),
            new GeminiModel(MODEL_ID, null),
            timeout != null ? new TimeoutConfiguration(timeout) : null));
  }
}
