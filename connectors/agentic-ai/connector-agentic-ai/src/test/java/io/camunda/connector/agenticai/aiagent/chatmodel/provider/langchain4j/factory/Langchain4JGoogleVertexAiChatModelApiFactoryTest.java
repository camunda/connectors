/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.auth.oauth2.ServiceAccountCredentials;
import dev.langchain4j.model.vertexai.gemini.VertexAiGeminiChatModel;
import dev.langchain4j.model.vertexai.gemini.VertexAiGeminiChatModel.VertexAiGeminiChatModelBuilder;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.ChatMessageConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.CloseableChatModelDelegate;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.Langchain4JChatModelApi;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory.ChatModelProviderTestSupport.ResultCaptor;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.aiagent.model.request.v1.GoogleVertexAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.GoogleVertexAiProviderConfiguration.GoogleVertexAiAuthentication.ApplicationDefaultCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v1.GoogleVertexAiProviderConfiguration.GoogleVertexAiAuthentication.ServiceAccountCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v1.GoogleVertexAiProviderConfiguration.GoogleVertexAiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v1.GoogleVertexAiProviderConfiguration.GoogleVertexAiModel;
import io.camunda.connector.agenticai.aiagent.model.request.v1.GoogleVertexAiProviderConfiguration.GoogleVertexAiModel.GoogleVertexAiModelParameters;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Langchain4JGoogleVertexAiChatModelApiFactoryTest {

  private static final String PROJECT_ID = "projectId";
  private static final String REGION = "us-central1";
  private static final String MODEL = "gemini-2.5-pro";

  private static final GoogleVertexAiModelParameters DEFAULT_MODEL_PARAMETERS =
      new GoogleVertexAiModelParameters(10, 1.0F, 0.8F, 100);

  private final Langchain4JGoogleVertexAiChatModelApiFactory provider =
      new Langchain4JGoogleVertexAiChatModelApiFactory(
          mock(ChatMessageConverter.class),
          mock(ToolSpecificationConverter.class),
          mock(JsonSchemaConverter.class),
          Langchain4JChatModelApi.DEFAULT_CAPABILITIES);

  @Test
  void createsGoogleVertexAiChatModel() {
    final var providerConfig =
        new GoogleVertexAiProviderConfiguration(
            new GoogleVertexAiConnection(
                PROJECT_ID,
                REGION,
                new ApplicationDefaultCredentialsAuthentication(),
                new GoogleVertexAiModel(MODEL, DEFAULT_MODEL_PARAMETERS)));

    testGoogleVertexAiChatModelBuilder(
        providerConfig,
        (builder) -> {
          verify(builder).location(REGION);
          verify(builder).project(PROJECT_ID);
          verify(builder).modelName(MODEL);
          verify(builder).maxOutputTokens(DEFAULT_MODEL_PARAMETERS.maxOutputTokens());
          verify(builder).temperature(DEFAULT_MODEL_PARAMETERS.temperature());
          verify(builder).topP(DEFAULT_MODEL_PARAMETERS.topP());
          verify(builder).topK(DEFAULT_MODEL_PARAMETERS.topK());
        });
  }

  @ParameterizedTest
  @NullSource
  @MethodSource("nullModelParameters")
  void createsGoogleVertexAiChatModelWithNullModelParameters(
      GoogleVertexAiModelParameters modelParameters) {
    final var providerConfig =
        new GoogleVertexAiProviderConfiguration(
            new GoogleVertexAiConnection(
                PROJECT_ID,
                REGION,
                new ApplicationDefaultCredentialsAuthentication(),
                new GoogleVertexAiModel(MODEL, modelParameters)));

    testGoogleVertexAiChatModelBuilder(
        providerConfig,
        (builder) -> {
          verify(builder, never()).maxOutputTokens(anyInt());
          verify(builder, never()).temperature(anyFloat());
          verify(builder, never()).topP(anyFloat());
          verify(builder, never()).topK(anyInt());
        });
  }

  @Test
  void createsGoogleVertexAiChatModelWithServiceAccountCredential() {
    final var providerConfig =
        new GoogleVertexAiProviderConfiguration(
            new GoogleVertexAiConnection(
                PROJECT_ID,
                REGION,
                new ServiceAccountCredentialsAuthentication("{}"),
                new GoogleVertexAiModel(MODEL, DEFAULT_MODEL_PARAMETERS)));

    try (final var staticMockedSac = mockStatic(ServiceAccountCredentials.class)) {
      final var mockedSac = mock(ServiceAccountCredentials.class);
      when(mockedSac.createScoped(anyString())).thenReturn(mockedSac);
      staticMockedSac.when(() -> ServiceAccountCredentials.fromStream(any())).thenReturn(mockedSac);

      testGoogleVertexAiChatModelBuilder(
          providerConfig,
          (builder) -> {
            verify(builder).location(REGION);
            verify(builder).project(PROJECT_ID);
            verify(builder).credentials(mockedSac);
            verify(builder).modelName(MODEL);
            verify(builder).maxOutputTokens(DEFAULT_MODEL_PARAMETERS.maxOutputTokens());
            verify(builder).temperature(DEFAULT_MODEL_PARAMETERS.temperature());
            verify(builder).topP(DEFAULT_MODEL_PARAMETERS.topP());
            verify(builder).topK(DEFAULT_MODEL_PARAMETERS.topK());
          });

      staticMockedSac.verify(() -> ServiceAccountCredentials.fromStream(any()));
    }
  }

  @Test
  void vertexAiGeminiChatModelImplementsAutoCloseable() {
    // VertexAiGeminiChatModel implements Closeable; the provider passes it as the resource to
    // CloseableChatModelDelegate. This test guards against a future langchain4j upgrade silently
    // removing that interface, which would make the close() call a no-op.
    assertThat(VertexAiGeminiChatModel.class).isAssignableTo(AutoCloseable.class);
  }

  private void testGoogleVertexAiChatModelBuilder(
      GoogleVertexAiProviderConfiguration providerConfig,
      ThrowingConsumer<VertexAiGeminiChatModelBuilder> builderAssertions) {
    final var chatModelBuilder = spy(VertexAiGeminiChatModel.builder());
    final var chatModelResultCaptor = new ResultCaptor<VertexAiGeminiChatModel>();
    doAnswer(chatModelResultCaptor).when(chatModelBuilder).build();

    try (MockedStatic<VertexAiGeminiChatModel> chatModelMock =
        mockStatic(VertexAiGeminiChatModel.class, Answers.CALLS_REAL_METHODS)) {
      chatModelMock.when(VertexAiGeminiChatModel::builder).thenReturn(chatModelBuilder);

      final var chatModel = provider.createChatModel(providerConfig);
      assertThat(chatModel).isNotNull().isInstanceOf(CloseableChatModelDelegate.class);
      final var delegate = ((CloseableChatModelDelegate) chatModel).delegate();
      assertThat(delegate).isInstanceOf(VertexAiGeminiChatModel.class);
      assertThat(delegate).isSameAs(chatModelResultCaptor.getResult());

      builderAssertions.accept(chatModelBuilder);
    }
  }

  static Stream<GoogleVertexAiModelParameters> nullModelParameters() {
    return Stream.of(new GoogleVertexAiModelParameters(null, null, null, null));
  }
}
