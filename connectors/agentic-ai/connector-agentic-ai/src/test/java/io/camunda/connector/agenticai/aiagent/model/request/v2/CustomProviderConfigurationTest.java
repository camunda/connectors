/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelRegistryImpl;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CustomProviderConfigurationTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void deserialisesViaTypeDiscriminatorAndRoundTrips() throws Exception {
    final String json =
        """
        {
          "type": "custom",
          "providerType": "acme-llm",
          "model": "acme-model-v1",
          "parameters": { "endpoint": "https://acme.example.com", "timeout": 30 }
        }
        """;

    final ProviderConfiguration parsed = mapper.readValue(json, ProviderConfiguration.class);

    assertThat(parsed).isInstanceOf(CustomProviderConfiguration.class);
    assertThat(parsed.provider()).isEqualTo("acme-llm");
    assertThat(parsed.model()).isEqualTo("acme-model-v1");
    assertThat(parsed.backend()).isNull();

    final CustomProviderConfiguration custom = (CustomProviderConfiguration) parsed;
    assertThat(custom.providerType()).isEqualTo("acme-llm");
    assertThat(custom.parameters())
        .containsEntry("endpoint", "https://acme.example.com")
        .containsEntry("timeout", 30);

    final String reserialised = mapper.writeValueAsString(parsed);
    assertThat(mapper.readValue(reserialised, ProviderConfiguration.class)).isEqualTo(parsed);
  }

  @Test
  void providerModelAndBackendAreDerivedFromDedicatedFields() {
    final var config =
        new CustomProviderConfiguration("acme-llm", "acme-model-v1", Map.of("key", "value"));

    assertThat(config.provider()).isEqualTo("acme-llm");
    assertThat(config.model()).isEqualTo("acme-model-v1");
    assertThat(config.backend()).isNull();
  }

  @Test
  void userSuppliedFactoryResolvesCustomProviderConfiguration() {
    final var config = new CustomProviderConfiguration("acme-llm", "acme-model-v1", Map.of());
    final var expectedChatModel = Mockito.mock(ChatModel.class);

    final ChatModelFactory userFactory =
        new ChatModelFactory() {
          @Override
          public boolean supports(ChatModelConfiguration configuration) {
            return configuration instanceof CustomProviderConfiguration custom
                && "acme-llm".equals(custom.providerType());
          }

          @Override
          public ChatModel create(ChatModelConfiguration configuration) {
            return expectedChatModel;
          }
        };

    final var registry = new ChatModelRegistryImpl(List.of(userFactory));

    assertThat(registry.resolve(config)).isSameAs(expectedChatModel);
  }

  @Test
  void resolvingWithoutARegisteredFactoryYieldsTheNoFactoryRegisteredError() {
    final var config = new CustomProviderConfiguration("acme-llm", "acme-model-v1", Map.of());

    final var registry = new ChatModelRegistryImpl(List.of());

    assertThatThrownBy(() -> registry.resolve(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("No chat model registered for configuration")
        .hasMessageContaining("acme-llm")
        .hasMessageContaining("acme-model-v1");
  }
}
