/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.mistral;

import static io.camunda.connector.agenticai.aiagent.ChatModelTranslationFixture.translate;
import static io.camunda.connector.agenticai.aiagent.chatmodel.envelope.ModelCallEnvelopeFixture.delegateOf;
import static io.camunda.connector.agenticai.aiagent.request.v2.MistralChatModelConfiguration.MISTRAL_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.connection.ConnectionResolver;
import io.camunda.connector.agenticai.aiagent.chatmodel.connection.RequestCustomizations;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.azure.FoundryCredentialResolver;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiContentConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsRequestConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsResponseConverter;
import io.camunda.connector.agenticai.aiagent.model.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.request.v2.CustomProviderConfiguration;
import io.camunda.connector.agenticai.common.http.AgenticAiHttpProxySupport;
import io.camunda.connector.http.client.authentication.OAuthClientCredentialsTokenResolver;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MistralChatModelFactoryTest {

  private static final String MODEL_ID = "mistral-medium-latest";

  @Mock private AgenticAiHttpProxySupport httpProxySupport;
  @Mock private OAuthClientCredentialsTokenResolver oAuthClientCredentialsTokenResolver;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private MistralChatModelFactory factory;

  @BeforeEach
  void setUp() {
    final var contentConverter = new OpenAiContentConverter(objectMapper);
    factory =
        new MistralChatModelFactory(
            new ConnectionResolver(
                httpProxySupport,
                oAuthClientCredentialsTokenResolver,
                mock(FoundryCredentialResolver.class)),
            new OpenAiCompletionsRequestConverter(
                MISTRAL_ID,
                contentConverter,
                new MistralDocumentUrlContentChunkStrategy(objectMapper),
                objectMapper,
                new MistralReasoningContentDialect()),
            new OpenAiCompletionsResponseConverter(
                MISTRAL_ID, objectMapper, new MistralReasoningContentDialect()),
            new MistralCompletionsStreamAssembler());
  }

  private static MistralConfiguration config(String modelId, Duration timeout) {
    return new MistralConfiguration(
        new MistralConfiguration.Backend.Api(
            "sk-mistral-test",
            MistralConfiguration.Backend.Api.DEFAULT_ENDPOINT,
            RequestCustomizations.NONE),
        modelId,
        timeout,
        null,
        null,
        null,
        null);
  }

  @Test
  void supportsOnlyMistralConfiguration() {
    assertThat(factory.supports(config(MODEL_ID, Duration.ofMinutes(3)))).isTrue();

    final ChatModelConfiguration otherProvider =
        translate(new CustomProviderConfiguration("some-custom-provider", MODEL_ID, Map.of()));
    assertThat(factory.supports(otherProvider)).isFalse();
  }

  @Test
  void createBuildsWorkingChatModel() {
    when(httpProxySupport.okHttpProxy(anyString())).thenReturn(Optional.empty());

    try (ChatModel chatModel = factory.create(config(MODEL_ID, Duration.ofMinutes(3)))) {
      assertThat(delegateOf(chatModel)).isInstanceOf(MistralChatModel.class);
    }
  }

  @Test
  void createsTheClientForTheConfiguredEndpoint() {
    when(httpProxySupport.okHttpProxy(anyString())).thenReturn(Optional.empty());

    final var clientBuilder = spy(OpenAIOkHttpClient.builder());
    try (MockedStatic<OpenAIOkHttpClient> clientMock =
        mockStatic(OpenAIOkHttpClient.class, Answers.CALLS_REAL_METHODS)) {
      clientMock.when(OpenAIOkHttpClient::builder).thenReturn(clientBuilder);

      try (ChatModel chatModel = factory.create(config(MODEL_ID, Duration.ofMinutes(3)))) {
        verify(clientBuilder).baseUrl(MistralConfiguration.Backend.Api.DEFAULT_ENDPOINT);
      }
    }
  }
}
