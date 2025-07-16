/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.suppliers;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.ai.inference.ChatCompletionsClient;
import com.azure.ai.openai.OpenAIClient;
import io.camunda.connector.idp.extraction.model.providers.AzureProvider;
import io.camunda.connector.idp.extraction.model.providers.azure.AiFoundryConfig;
import io.camunda.connector.idp.extraction.supplier.AzureAiFoundrySupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AzureAiFoundrySupplierTest {

  private AzureProvider azureProvider;

  @BeforeEach
  public void setUp() {
    azureProvider = createAzureProvider();
  }

  @Test
  void getChatCompletionsClient() {
    ChatCompletionsClient client = AzureAiFoundrySupplier.getChatCompletionsClient(azureProvider);
    assertThat(client).isInstanceOf(ChatCompletionsClient.class);
  }

  @Test
  void getOpenAIClient() {
    OpenAIClient client = AzureAiFoundrySupplier.getOpenAIClient(azureProvider);
    assertThat(client).isInstanceOf(OpenAIClient.class);
  }

  private AzureProvider createAzureProvider() {
    AiFoundryConfig config = createAIFoundryConfig();
    AzureProvider provider = new AzureProvider();
    provider.setAiFoundryConfig(config);
    return provider;
  }

  private AiFoundryConfig createAIFoundryConfig() {
    AiFoundryConfig config = new AiFoundryConfig();
    config.setEndpoint("https://test-foundry.openai.azure.com/");
    config.setApiKey("test-api-key");
    config.setUsingOpenAI(false);
    return config;
  }
}
