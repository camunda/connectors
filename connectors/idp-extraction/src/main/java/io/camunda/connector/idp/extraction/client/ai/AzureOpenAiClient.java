/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.client.ai;

import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import io.camunda.connector.idp.extraction.client.ai.base.AiClient;
import io.camunda.connector.idp.extraction.model.ConverseData;

public class AzureOpenAiClient extends AiClient {

  public AzureOpenAiClient(String endpoint, String apiKey, ConverseData converseData) {

    var builder =
        AzureOpenAiChatModel.builder()
            .endpoint(endpoint)
            .apiKey(apiKey)
            .deploymentName(converseData.modelId());

    if (converseData.maxTokens() != null) {
      builder.maxTokens(converseData.maxTokens());
    }
    if (converseData.temperature() != null) {
      builder.temperature(converseData.temperature().doubleValue());
    }
    if (converseData.topP() != null) {
      builder.topP(converseData.topP().doubleValue());
    }

    this.chatModel = builder.build();
  }

  @Override
  public String chatWithPdf(String textPrompt, String pdfUrl) {
    throw new UnsupportedOperationException(
        "Azure OpenAI models do not support PDF content type directly.");
  }
}
