/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.caller;

import io.camunda.connector.idp.extraction.client.ai.AzureAiFoundryClient;
import io.camunda.connector.idp.extraction.client.ai.AzureOpenAiClient;
import io.camunda.connector.idp.extraction.client.ai.base.AiClient;
import io.camunda.connector.idp.extraction.model.ExtractionRequestData;
import io.camunda.connector.idp.extraction.model.LlmModel;
import io.camunda.connector.idp.extraction.model.providers.AzureProvider;
import io.camunda.connector.idp.extraction.model.providers.azure.AiFoundryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AzureAiFoundryCaller {

  private static final Logger LOGGER = LoggerFactory.getLogger(AzureAiFoundryCaller.class);

  public String call(ExtractionRequestData input, AzureProvider baseRequest, String extractedText) {
    LOGGER.debug("Calling Azure AI Foundry with extraction request data: {}", input);

    try {
      LlmModel llmModel = LlmModel.fromId(input.converseData().modelId());
      AiClient aiClient = createAiClient(input, baseRequest);

      String userMessage = llmModel.getMessage(extractedText, input.taxonomyItems());
      String responseText =
          llmModel.isSystemPromptAllowed()
              ? aiClient.chat(llmModel.getSystemPrompt(), userMessage)
              : aiClient.chat(String.format("%s%n%s", llmModel.getSystemPrompt(), userMessage));
      LOGGER.debug("Azure AI Foundry response: {}", responseText);

      return responseText;
    } catch (Exception e) {
      LOGGER.error("Error calling Azure AI Foundry", e);
      throw new RuntimeException("Failed to call Azure AI Foundry: " + e.getMessage(), e);
    }
  }

  AiClient createAiClient(ExtractionRequestData input, AzureProvider baseRequest) {
    AiFoundryConfig config = baseRequest.getAiFoundryConfig();
    return config.isUsingOpenAI()
        ? new AzureOpenAiClient(config.getEndpoint(), config.getApiKey(), input.converseData())
        : new AzureAiFoundryClient(config.getEndpoint(), config.getApiKey(), input.converseData());
  }
}
