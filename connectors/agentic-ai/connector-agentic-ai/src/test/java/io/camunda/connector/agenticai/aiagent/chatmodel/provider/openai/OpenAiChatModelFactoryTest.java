/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsRequestConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsResponseConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsStrategy;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsStreamAssembler;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.responses.OpenAiResponsesRequestConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.responses.OpenAiResponsesResponseConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.responses.OpenAiResponsesStrategy;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.responses.OpenAiResponsesStreamAssembler;
import io.camunda.connector.agenticai.aiagent.model.request.v2.CustomProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiCompletionsApi;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiCompletionsApi.CompletionsParameters;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiResponsesApi;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiResponsesApi.ResponsesParameters;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiApiBackend.OpenAiApiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiCustomBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiCustomBackend.CustomBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.shared.CustomEndpointAuthentication.NoAuthentication;
import io.camunda.connector.agenticai.common.AgenticAiHttpProxySupport;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpenAiChatModelFactoryTest {

  private static final String MODEL_ID = "gpt-5.5";

  @Mock private AgenticAiHttpProxySupport httpProxySupport;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private OpenAiChatModelFactory factory;

  @BeforeEach
  void setUp() {
    final var contentConverter = new OpenAiContentConverter(objectMapper);
    factory =
        new OpenAiChatModelFactory(
            httpProxySupport,
            new OpenAiCompletionsStrategy(
                new OpenAiCompletionsRequestConverter(contentConverter, objectMapper),
                new OpenAiCompletionsResponseConverter(objectMapper),
                OpenAiCompletionsStreamAssembler.accumulating()),
            new OpenAiResponsesStrategy(
                new OpenAiResponsesRequestConverter(contentConverter, objectMapper),
                new OpenAiResponsesResponseConverter(objectMapper),
                OpenAiResponsesStreamAssembler.accumulating()));
  }

  @Test
  void supportsOnlyOpenAiConfiguration() {
    assertThat(factory.supports(responsesApiConfig(MODEL_ID))).isTrue();

    final ChatModelConfiguration otherProvider =
        new CustomProviderConfiguration("some-custom-provider", MODEL_ID, Map.of());
    assertThat(factory.supports(otherProvider)).isFalse();
  }

  @ParameterizedTest
  @MethodSource("configs")
  void createBuildsWorkingApi(OpenAiChatModelConfiguration config) {
    when(httpProxySupport.okHttpProxy(any())).thenReturn(Optional.empty());

    final ChatModel api = factory.create(config);

    assertThat(api).isNotNull().isInstanceOf(OpenAiChatModel.class);
    api.close();
  }

  static Stream<OpenAiChatModelConfiguration> configs() {
    return Stream.of(
        responsesApiConfig(MODEL_ID), customConfig(MODEL_ID), completionsApiConfig(MODEL_ID));
  }

  private static OpenAiChatModelConfiguration responsesApiConfig(String modelId) {
    return new OpenAiChatModelConfiguration(
        new OpenAiChatModelConfiguration.OpenAiConnection(
            new OpenAiResponsesApi(new ResponsesParameters(null, null, null, null)),
            new OpenAiApiBackend(
                new OpenAiApiConnection("sk-openai-test", null, null, null, null, null, null)),
            new OpenAiModel(modelId),
            null));
  }

  private static OpenAiChatModelConfiguration completionsApiConfig(String modelId) {
    return new OpenAiChatModelConfiguration(
        new OpenAiChatModelConfiguration.OpenAiConnection(
            new OpenAiCompletionsApi(new CompletionsParameters(null, null, null, null)),
            new OpenAiApiBackend(
                new OpenAiApiConnection("sk-openai-test", null, null, null, null, null, null)),
            new OpenAiModel(modelId),
            null));
  }

  private static OpenAiChatModelConfiguration customConfig(String modelId) {
    return new OpenAiChatModelConfiguration(
        new OpenAiChatModelConfiguration.OpenAiConnection(
            new OpenAiResponsesApi(new ResponsesParameters(null, null, null, null)),
            new OpenAiCustomBackend(
                new CustomBackend(
                    "https://custom.example.com", null, null, null, new NoAuthentication())),
            new OpenAiModel(modelId),
            null));
  }
}
