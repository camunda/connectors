/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.supplier;

import com.azure.ai.inference.ChatCompletionsClient;
import com.azure.ai.inference.ChatCompletionsClientBuilder;
import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import io.camunda.connector.idp.extraction.model.providers.AzureProvider;
import io.camunda.connector.idp.extraction.model.providers.azure.AIFoundryConfig;

public final class AzureAiFoundrySupplier {

  private AzureAiFoundrySupplier() {}

  public static ChatCompletionsClient getChatCompletionsClient(AzureProvider baseRequest) {
    AIFoundryConfig configuration = baseRequest.getAiFoundryConfig();
    return new ChatCompletionsClientBuilder()
        .endpoint(configuration.getEndpoint())
        .credential(new AzureKeyCredential(configuration.getApiKey()))
        .buildClient();
  }

  public static OpenAIClient getOpenAIClient(AzureProvider baseRequest) {
    AIFoundryConfig configuration = baseRequest.getAiFoundryConfig();
    return new OpenAIClientBuilder()
        .endpoint(configuration.getEndpoint())
        .credential(new AzureKeyCredential(configuration.getApiKey()))
        .buildClient();
  }
}
