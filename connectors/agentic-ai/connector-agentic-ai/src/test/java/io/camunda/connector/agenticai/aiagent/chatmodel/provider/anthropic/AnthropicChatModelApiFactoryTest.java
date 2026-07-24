/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicCompatibleBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.CustomProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.shared.CompatibleAuthentication.CompatibleNoAuthentication;
import io.camunda.connector.agenticai.aiagent.transport.HttpTransportSupport;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnthropicChatModelApiFactoryTest {

  private static final String MODEL_ID = "claude-sonnet-4-6";

  @Mock private HttpTransportSupport transport;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private AnthropicChatModelApiFactory factory;

  @BeforeEach
  void setUp() {
    factory = new AnthropicChatModelApiFactory(transport, objectMapper);
  }

  @Test
  void supportsAnthropicApiV2Config() {
    assertThat(factory.supports(apiConfig(MODEL_ID))).isTrue();
  }

  @Test
  void supportsAnthropicCompatibleV2Config() {
    assertThat(factory.supports(compatibleConfig(MODEL_ID))).isTrue();
  }

  @Test
  void doesNotSupportOtherProviderConfiguration() {
    final ChatModelConfiguration config =
        new CustomProviderConfiguration("some-custom-provider", MODEL_ID, Map.of());

    assertThat(factory.supports(config)).isFalse();
  }

  @Test
  void createBuildsWorkingApiForApiBackend() {
    when(transport.okHttpProxy(any())).thenReturn(Optional.empty());

    final ChatModel api = factory.create(apiConfig(MODEL_ID));

    assertThat(api).isNotNull().isInstanceOf(AnthropicChatModelApi.class);
    api.close();
  }

  @Test
  void createBuildsWorkingApiForCompatibleBackend() {
    when(transport.okHttpProxy(any())).thenReturn(Optional.empty());

    final ChatModel api = factory.create(compatibleConfig(MODEL_ID));

    assertThat(api).isNotNull().isInstanceOf(AnthropicChatModelApi.class);
    api.close();
  }

  private static AnthropicChatModelConfiguration apiConfig(String modelId) {
    return new AnthropicChatModelConfiguration(
        new AnthropicConnection(
            new AnthropicApiBackend("sk-ant-test"), new AnthropicModel(modelId, null), null));
  }

  private static AnthropicChatModelConfiguration compatibleConfig(String modelId) {
    return new AnthropicChatModelConfiguration(
        new AnthropicConnection(
            new AnthropicCompatibleBackend(
                "https://compatible.example.com",
                null,
                null,
                null,
                new CompatibleNoAuthentication()),
            new AnthropicModel(modelId, null),
            null));
  }
}
