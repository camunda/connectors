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
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
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
  void createWiresProxyOptionsWhenProxyConfigured() {
    when(httpProxySupport.getProxyConfiguration()).thenReturn(proxyConfiguration);
    when(proxyConfiguration.getProxyDetails(any()))
        .thenReturn(
            Optional.of(
                new ProxyConfiguration.ProxyDetails(
                    "https", "proxy.example.com", 8080, "proxy-user", "proxy-pass")));

    final ChatModel model = factory.create(apiConfig(null, null));

    final ApiClient apiClient = apiClientOf(model);
    @SuppressWarnings("unchecked")
    final Optional<ClientOptions> clientOptions =
        (Optional<ClientOptions>) ReflectionTestUtils.getField(apiClient, "clientOptions");

    assertThat(clientOptions).isPresent();
    final var proxyOptions = clientOptions.get().proxyOptions();
    assertThat(proxyOptions).isPresent();
    assertThat(proxyOptions.get().host()).contains("proxy.example.com");
    assertThat(proxyOptions.get().port()).contains(8080);
    assertThat(proxyOptions.get().username()).contains("proxy-user");
    assertThat(proxyOptions.get().password()).contains("proxy-pass");

    verify(proxyConfiguration).getProxyDetails(ProxyConfiguration.SCHEME_HTTPS);

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

  private static GeminiChatModelConfiguration apiConfig(
      @Nullable String endpoint, @Nullable Duration timeout) {
    return new GeminiChatModelConfiguration(
        new GeminiConnection(
            new GeminiApiBackend(new GoogleGeminiApi(API_KEY, endpoint)),
            new GeminiModel(MODEL_ID, null),
            timeout != null ? new TimeoutConfiguration(timeout) : null));
  }
}
