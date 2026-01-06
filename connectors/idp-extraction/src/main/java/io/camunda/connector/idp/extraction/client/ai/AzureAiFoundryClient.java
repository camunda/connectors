/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.client.ai;

import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import io.camunda.connector.idp.extraction.client.ai.base.AiClient;
import io.camunda.connector.idp.extraction.model.ConverseData;

public class AzureAiFoundryClient extends AiClient {

  public AzureAiFoundryClient(String endpoint, String apiKey, ConverseData converseData) {
    // For AI Foundry serverless endpoints, the model name is used as the deployment name
    // The endpoint should be the base URL without the model name
    // e.g., https://idp-ai-provider.services.ai.azure.com
    String baseEndpoint = endpoint.replace("/models", "").replaceAll("/$", "");

    var builder =
        AzureOpenAiChatModel.builder()
            .responseFormat(ResponseFormat.JSON)
            .endpoint(baseEndpoint)
            .apiKey(apiKey)
            .deploymentName(converseData.modelId());

    // Commenting out the max tokens assignment because it negatively impacts responses
    //    if (converseData.maxTokens() != null) {
    //      builder.maxTokens(converseData.maxTokens());
    //    }
    if (converseData.temperature() != null) {
      builder.temperature(converseData.temperature().doubleValue());
    }
    if (converseData.topP() != null) {
      builder.topP(converseData.topP().doubleValue());
    }

    this.chatModel = builder.build();
  }
}
