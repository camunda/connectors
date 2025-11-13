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
    this.chatModel =
        AzureOpenAiChatModel.builder()
            .endpoint(endpoint)
            .apiKey(apiKey)
            .deploymentName(converseData.modelId())
            .temperature(Double.valueOf(converseData.temperature()))
            .topP(Double.valueOf(converseData.topP()))
            .build();
  }

  @Override
  public String chatWithPdf(String textPrompt, String pdfUrl) {
    throw new UnsupportedOperationException(
        "Azure OpenAI models do not support PDF content type directly.");
  }
}
