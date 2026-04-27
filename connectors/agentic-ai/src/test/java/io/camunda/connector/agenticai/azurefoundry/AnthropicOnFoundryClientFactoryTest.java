/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.azurefoundry;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ChatModelHttpProxySupport;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureFoundryProviderConfiguration.AzureAiFoundryModel.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureFoundryProviderConfiguration.AzureAiFoundryModel.AnthropicModel.AnthropicModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.AzureAuthentication.AzureApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.AzureAuthentication.AzureClientCredentialsAuthentication;
import io.camunda.connector.agenticai.azurefoundry.langchain4j.AnthropicOnFoundryChatModel;
import io.camunda.connector.http.client.client.jdk.proxy.JdkHttpClientProxyConfigurator;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AnthropicOnFoundryClientFactoryTest {

  private final ChatModelHttpProxySupport proxySupport =
      new ChatModelHttpProxySupport(
          ProxyConfiguration.NONE, new JdkHttpClientProxyConfigurator(ProxyConfiguration.NONE));

  private final AnthropicOnFoundryClientFactory factory =
      new AnthropicOnFoundryClientFactory(proxySupport);

  @Test
  void builds_anthropic_chat_model_with_api_key_auth() {
    AnthropicOnFoundryChatModel chatModel =
        factory.create(
            "https://my-resource.services.ai.azure.com",
            new AzureApiKeyAuthentication("api-key-value"),
            Duration.ofSeconds(30),
            new AnthropicModel(
                "claude-sonnet-4-6", new AnthropicModelParameters(1024, 0.7, 0.9, 40)));

    assertThat(chatModel).isNotNull();
    assertThat(chatModel.modelConfig().deploymentName()).isEqualTo("claude-sonnet-4-6");
  }

  @Test
  void builds_chat_model_even_when_endpoint_has_trailing_slash() {
    AnthropicOnFoundryChatModel chatModel =
        factory.create(
            "https://my-resource.services.ai.azure.com/",
            new AzureApiKeyAuthentication("api-key"),
            null,
            new AnthropicModel("claude-sonnet-4-6", null));

    assertThat(chatModel).isNotNull();
  }

  @Test
  void builds_anthropic_chat_model_with_client_credentials_auth() {
    AnthropicOnFoundryChatModel chatModel =
        factory.create(
            "https://my-resource.services.ai.azure.com",
            new AzureClientCredentialsAuthentication(
                "client-id", "client-secret", "tenant-id", null),
            null,
            new AnthropicModel("claude-sonnet-4-6", null));

    // Bearer-token supplier construction does not hit Azure until the first request is made.
    assertThat(chatModel).isNotNull();
  }

  @Test
  void uses_custom_authority_host_when_provided() {
    AnthropicOnFoundryChatModel chatModel =
        factory.create(
            "https://my-resource.services.ai.azure.com",
            new AzureClientCredentialsAuthentication(
                "client-id", "client-secret", "tenant-id", "https://login.microsoftonline.us"),
            null,
            new AnthropicModel("claude-sonnet-4-6", null));

    assertThat(chatModel).isNotNull();
  }
}
