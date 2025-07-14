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
import io.camunda.connector.idp.extraction.model.providers.azure.AIFoundryConfig;
import io.camunda.connector.idp.extraction.supplier.AzureAIFoundrySupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AzureAIFoundrySupplierTest {

  private AzureProvider azureProvider;

  @BeforeEach
  public void setUp() {
    azureProvider = createAzureProvider();
  }

  @Test
  void getChatCompletionsClient() {
    ChatCompletionsClient client = AzureAIFoundrySupplier.getChatCompletionsClient(azureProvider);
    assertThat(client).isInstanceOf(ChatCompletionsClient.class);
  }

  @Test
  void getOpenAIClient() {
    OpenAIClient client = AzureAIFoundrySupplier.getOpenAIClient(azureProvider);
    assertThat(client).isInstanceOf(OpenAIClient.class);
  }

  private AzureProvider createAzureProvider() {
    AIFoundryConfig config = createAIFoundryConfig();
    AzureProvider provider = new AzureProvider();
    provider.setAiFoundryConfig(config);
    return provider;
  }

  private AIFoundryConfig createAIFoundryConfig() {
    AIFoundryConfig config = new AIFoundryConfig();
    // Using reflection to set private fields since there are no public setters
    try {
      var endpointField = AIFoundryConfig.class.getDeclaredField("endpoint");
      endpointField.setAccessible(true);
      endpointField.set(config, "https://test-foundry.openai.azure.com/");

      var apiKeyField = AIFoundryConfig.class.getDeclaredField("apiKey");
      apiKeyField.setAccessible(true);
      apiKeyField.set(config, "test-api-key");

      var usingOpenAIField = AIFoundryConfig.class.getDeclaredField("usingOpenAI");
      usingOpenAIField.setAccessible(true);
      usingOpenAIField.set(config, false);
    } catch (Exception e) {
      throw new RuntimeException("Failed to set AIFoundryConfig fields", e);
    }
    return config;
  }
}
