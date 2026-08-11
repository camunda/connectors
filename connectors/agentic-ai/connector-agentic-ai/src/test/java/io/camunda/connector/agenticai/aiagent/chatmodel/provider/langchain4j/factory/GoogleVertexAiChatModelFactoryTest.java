/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory;

import static io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory.ChatModelProviderTestSupport.MODEL_TIMEOUT;
import static io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory.ChatModelProviderTestSupport.createDefaultChatModelProperties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.genai.Client;
import com.google.genai.types.ClientOptions;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.ProxyOptions;
import com.google.genai.types.ProxyType;
import dev.langchain4j.model.google.genai.GoogleGenAiChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.ChatMessageConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.ChatModelHttpProxySupport;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.CloseableChatModelDelegate;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory.ChatModelProviderTestSupport.ResultCaptor;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.aiagent.model.request.v1.GoogleVertexAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.GoogleVertexAiProviderConfiguration.GoogleVertexAiAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v1.GoogleVertexAiProviderConfiguration.GoogleVertexAiAuthentication.ApplicationDefaultCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v1.GoogleVertexAiProviderConfiguration.GoogleVertexAiAuthentication.ServiceAccountCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v1.GoogleVertexAiProviderConfiguration.GoogleVertexAiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v1.GoogleVertexAiProviderConfiguration.GoogleVertexAiModel;
import io.camunda.connector.agenticai.aiagent.model.request.v1.GoogleVertexAiProviderConfiguration.GoogleVertexAiModel.GoogleVertexAiModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.v1.shared.TimeoutConfiguration;
import io.camunda.connector.api.error.ConnectorInputException;
import java.time.Duration;
import java.util.Optional;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleVertexAiChatModelFactoryTest {

  private static final String PROJECT_ID = "projectId";
  private static final String REGION = "us-central1";
  private static final String MODEL = "gemini-2.5-pro";

  private static final GoogleVertexAiModelParameters DEFAULT_MODEL_PARAMETERS =
      new GoogleVertexAiModelParameters(10, 1.0F, 0.8F, 100);

  @Captor private ArgumentCaptor<HttpOptions> httpOptionsArgumentCaptor;
  @Captor private ArgumentCaptor<ClientOptions> clientOptionsArgumentCaptor;

  private final ChatModelHttpProxySupport proxySupport = mock(ChatModelHttpProxySupport.class);

  private final GoogleVertexAiChatModelFactory factory =
      new GoogleVertexAiChatModelFactory(
          createDefaultChatModelProperties(),
          proxySupport,
          mock(ChatMessageConverter.class),
          mock(ToolSpecificationConverter.class),
          mock(JsonSchemaConverter.class));

  @Test
  void createsGoogleVertexAiChatModel() {
    testGoogleVertexAiChatModelBuilder(
        createProviderConfig(
            null,
            null,
            new ApplicationDefaultCredentialsAuthentication(),
            DEFAULT_MODEL_PARAMETERS),
        (builders) -> {
          verify(builders.clientBuilder).vertexAI(true);
          verify(builders.clientBuilder).project(PROJECT_ID);
          verify(builders.clientBuilder).location(REGION);
          verify(builders.clientBuilder, never()).credentials(any());

          verify(builders.chatModelBuilder).client(builders.client);
          verify(builders.chatModelBuilder).modelName(MODEL);
          verify(builders.chatModelBuilder).maxRetries(0);
          verify(builders.chatModelBuilder)
              .maxOutputTokens(DEFAULT_MODEL_PARAMETERS.maxOutputTokens());
          verify(builders.chatModelBuilder)
              .temperature(DEFAULT_MODEL_PARAMETERS.temperature().doubleValue());
          verify(builders.chatModelBuilder).topP(DEFAULT_MODEL_PARAMETERS.topP().doubleValue());
          verify(builders.chatModelBuilder).topK(DEFAULT_MODEL_PARAMETERS.topK());
        });
  }

  @ParameterizedTest
  @NullSource
  @MethodSource("nullModelParameters")
  void createsGoogleVertexAiChatModelWithNullModelParameters(
      GoogleVertexAiModelParameters modelParameters) {
    testGoogleVertexAiChatModelBuilder(
        createProviderConfig(
            null, null, new ApplicationDefaultCredentialsAuthentication(), modelParameters),
        (builders) -> {
          verify(builders.chatModelBuilder, never()).maxOutputTokens(anyInt());
          verify(builders.chatModelBuilder, never()).temperature(anyDouble());
          verify(builders.chatModelBuilder, never()).topP(anyDouble());
          verify(builders.chatModelBuilder, never()).topK(anyInt());
        });
  }

  /**
   * Service account credentials must be scoped explicitly. google-genai only scopes the application
   * default credentials it resolves itself and passes user-supplied credentials through verbatim,
   * so an unscoped credential makes the token request fail with {@code invalid_scope}.
   */
  @Test
  void createsGoogleVertexAiChatModelWithServiceAccountCredential() {
    try (final var staticMockedSac = mockStatic(ServiceAccountCredentials.class)) {
      final var mockedSac = mock(ServiceAccountCredentials.class);
      final var scopedSac = mock(GoogleCredentials.class);
      when(mockedSac.createScoped("https://www.googleapis.com/auth/cloud-platform"))
          .thenReturn(scopedSac);
      staticMockedSac.when(() -> ServiceAccountCredentials.fromStream(any())).thenReturn(mockedSac);

      testGoogleVertexAiChatModelBuilder(
          createProviderConfig(
              null,
              null,
              new ServiceAccountCredentialsAuthentication("{}"),
              DEFAULT_MODEL_PARAMETERS),
          (builders) -> verify(builders.clientBuilder).credentials(scopedSac));

      staticMockedSac.verify(() -> ServiceAccountCredentials.fromStream(any()));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"global", "us", "us-central1", "europe-west1"})
  void passesRegionThroughUnchanged(String region) {
    final var providerConfig =
        new GoogleVertexAiProviderConfiguration(
            new GoogleVertexAiConnection(
                PROJECT_ID,
                region,
                null,
                new ApplicationDefaultCredentialsAuthentication(),
                null,
                new GoogleVertexAiModel(MODEL, null)));

    testGoogleVertexAiChatModelBuilder(
        providerConfig, (builders) -> verify(builders.clientBuilder).location(region));
  }

  @Test
  void disablesGenAiRetries() {
    testGoogleVertexAiChatModelBuilder(
        createProviderConfig(null, null, new ApplicationDefaultCredentialsAuthentication(), null),
        (builders) ->
            assertThat(captureHttpOptions(builders).retryOptions())
                .isPresent()
                .get()
                .extracting(retryOptions -> retryOptions.attempts().orElseThrow())
                .isEqualTo(1));
  }

  @Test
  void appliesExplicitlyConfiguredTimeout() {
    testGoogleVertexAiChatModelBuilder(
        createProviderConfig(
            null, MODEL_TIMEOUT, new ApplicationDefaultCredentialsAuthentication(), null),
        (builders) ->
            assertThat(captureHttpOptions(builders).timeout()).isPresent().contains(30_000));
  }

  @ParameterizedTest
  @NullSource
  @MethodSource(
      "io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory.ChatModelProviderTestSupport#defaultTimeoutYieldingConfigs")
  void appliesDefaultTimeoutWhenNoneIsConfigured(TimeoutConfiguration timeouts) {
    testGoogleVertexAiChatModelBuilder(
        createProviderConfig(
            null, timeouts, new ApplicationDefaultCredentialsAuthentication(), null),
        (builders) ->
            assertThat(captureHttpOptions(builders).timeout())
                .isPresent()
                .contains((int) Duration.ofMinutes(3).toMillis()));
  }

  @Test
  void throwsForTimeoutExceedingIntegerMillisRange() {
    final var providerConfig =
        createProviderConfig(
            null,
            new TimeoutConfiguration(Duration.ofDays(30)),
            new ApplicationDefaultCredentialsAuthentication(),
            null);

    assertThatThrownBy(() -> factory.createChatModel(providerConfig))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("exceeds the maximum supported by the Google GenAI SDK");
  }

  @Test
  void appliesCustomEndpoint() {
    testGoogleVertexAiChatModelBuilder(
        createProviderConfig(
            "https://my-custom-endpoint.local",
            null,
            new ApplicationDefaultCredentialsAuthentication(),
            null),
        (builders) ->
            assertThat(captureHttpOptions(builders).baseUrl())
                .isPresent()
                .contains("https://my-custom-endpoint.local"));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "  "})
  void doesNotApplyBlankEndpoint(String endpoint) {
    testGoogleVertexAiChatModelBuilder(
        createProviderConfig(
            endpoint, null, new ApplicationDefaultCredentialsAuthentication(), null),
        (builders) -> assertThat(captureHttpOptions(builders).baseUrl()).isEmpty());
  }

  @Test
  void doesNotApplyClientOptionsWhenProxyNotConfigured() {
    testGoogleVertexAiChatModelBuilder(
        createProviderConfig(null, null, new ApplicationDefaultCredentialsAuthentication(), null),
        (builders) -> verify(builders.clientBuilder, never()).clientOptions(any()));
  }

  @Test
  void appliesProxyOptionsWhenConfigured() {
    final var proxyOptions =
        ProxyOptions.builder().type(ProxyType.Known.HTTP).host("proxy.local").port(8080).build();
    doReturn(Optional.of(proxyOptions))
        .when(proxySupport)
        .createGoogleGenAiProxyOptions(any(), any());

    testGoogleVertexAiChatModelBuilder(
        createProviderConfig(null, null, new ApplicationDefaultCredentialsAuthentication(), null),
        (builders) -> {
          verify(builders.clientBuilder).clientOptions(clientOptionsArgumentCaptor.capture());
          assertThat(clientOptionsArgumentCaptor.getValue().proxyOptions()).contains(proxyOptions);
        });
  }

  @Test
  void throwsWhenClientCreationFails() {
    final var providerConfig =
        createProviderConfig(null, null, new ApplicationDefaultCredentialsAuthentication(), null);

    final var clientBuilder = spy(Client.builder());
    doAnswer(
            invocation -> {
              throw new IllegalArgumentException("boom");
            })
        .when(clientBuilder)
        .build();

    try (MockedStatic<Client> clientMock = mockStatic(Client.class, Answers.CALLS_REAL_METHODS)) {
      clientMock.when(Client::builder).thenReturn(clientBuilder);

      assertThatThrownBy(() -> factory.createChatModel(providerConfig))
          .isInstanceOf(ConnectorInputException.class)
          .hasMessageContaining("Failed to create Google Vertex AI client");
    }
  }

  private HttpOptions captureHttpOptions(GoogleVertexAiBuilderContext builders) {
    verify(builders.clientBuilder).httpOptions(httpOptionsArgumentCaptor.capture());
    return httpOptionsArgumentCaptor.getValue();
  }

  private static GoogleVertexAiProviderConfiguration createProviderConfig(
      String endpoint,
      TimeoutConfiguration timeouts,
      GoogleVertexAiAuthentication authentication,
      GoogleVertexAiModelParameters modelParameters) {
    return new GoogleVertexAiProviderConfiguration(
        new GoogleVertexAiConnection(
            PROJECT_ID,
            REGION,
            endpoint,
            authentication,
            timeouts,
            new GoogleVertexAiModel(MODEL, modelParameters)));
  }

  private void testGoogleVertexAiChatModelBuilder(
      GoogleVertexAiProviderConfiguration providerConfig,
      ThrowingConsumer<GoogleVertexAiBuilderContext> builderAssertions) {
    // the client must not really be built - it would resolve application default credentials
    final var client = mock(Client.class);
    final var clientBuilder = spy(Client.builder());
    doReturn(client).when(clientBuilder).build();

    final var chatModelBuilder = spy(GoogleGenAiChatModel.builder());
    final var chatModelResultCaptor = new ResultCaptor<GoogleGenAiChatModel>();
    doAnswer(chatModelResultCaptor).when(chatModelBuilder).build();

    try (MockedStatic<Client> clientMock = mockStatic(Client.class, Answers.CALLS_REAL_METHODS);
        MockedStatic<GoogleGenAiChatModel> chatModelMock =
            mockStatic(GoogleGenAiChatModel.class, Answers.CALLS_REAL_METHODS)) {
      clientMock.when(Client::builder).thenReturn(clientBuilder);
      chatModelMock.when(GoogleGenAiChatModel::builder).thenReturn(chatModelBuilder);

      final var chatModel = factory.createChatModel(providerConfig);
      assertThat(chatModel).isNotNull().isInstanceOf(CloseableChatModelDelegate.class);
      assertThat(((CloseableChatModelDelegate) chatModel).delegate())
          .isSameAs(chatModelResultCaptor.getResult());
      // GoogleGenAiChatModel is not closeable - the client is the resource we must release
      assertThat(((CloseableChatModelDelegate) chatModel).resource()).isSameAs(client);

      verify(proxySupport)
          .createGoogleGenAiProxyOptions(
              providerConfig.googleVertexAi().endpoint(), providerConfig.googleVertexAi().region());

      builderAssertions.accept(
          new GoogleVertexAiBuilderContext(clientBuilder, client, chatModelBuilder));
    }
  }

  static Stream<GoogleVertexAiModelParameters> nullModelParameters() {
    return Stream.of(new GoogleVertexAiModelParameters(null, null, null, null));
  }

  private record GoogleVertexAiBuilderContext(
      Client.Builder clientBuilder, Client client, GoogleGenAiChatModel.Builder chatModelBuilder) {}
}
